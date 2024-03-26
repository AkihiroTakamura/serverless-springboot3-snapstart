# serverless-springboot3-snapstart

## ref

[aws 公式 snapstart documents](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html)

[aws 公式 serverless springboot3](https://github.com/aws/serverless-java-container/wiki/Quick-start---Spring-Boot3)

[sample source](https://github.com/aws/serverless-java-container/tree/main/samples/springboot3/pet-store)

## build and deploy

```
sam build
```

```
export AWS_PROFILE=xxxxx
sam deploy --guided
```

Once the deployment is completed, the SAM CLI will print out the stack's outputs, including the new application URL. You can use curl or a web browser to make a call to the URL

```
...
---------------------------------------------------------------------------------------------------------
OutputKey-Description                        OutputValue
---------------------------------------------------------------------------------------------------------
PetStoreApi - URL for application            https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/pets
---------------------------------------------------------------------------------------------------------

$ curl https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/pets
```

## snap start

### 有効にするには

1. lambda 関数の configuration にて `SnapStart` が `PublishedVersions` に設定されている必要がある

   - `template.yml` 内 `SnapStart:ApplyOn: PublishedVersions` の指定によって設定している

マネコンから見た時、このようになっていれば OK

![picture 0](https://i.imgur.com/mDH3tmp.png)

2. lambda のバージョン publish が必要

   - `template.yml` 内 `AutoPublishAlias` の指定によってデプロイ時に自動でバージョン・エイリアスが発行されるようにしている
   - これをしない場合、cli やマネコンから lambda の新しいバージョンを発行してやらないと snap start が有効にならない

3. snapstart の状態確認

   - `aws lambda get-function-configuration --function-name my-function:1` のようにバージョン指定で情報を参照する
   - `"State": "Active"` のように `Active` で返ってくれば snapstart の準備 OK

4. snapstart の動作

   - 対象 endpoint に request を送付
     - lambda が cold の状態の場合、cloudwatch に `RESTORE_START` と表示される
     - ここで snapshot が restore され、処理を実行後に response される
     - snapshot に preload する必要があるものを詰めておくことで短縮ができる

### tuning

[参考](https://docs.aws.amazon.com/lambda/latest/dg/snapstart-best-practices.html#snapstart-tuning)

snapstart を有効にした場合でも、restore の後の対象 class ロードで時間がかかる（2000ms 程度）

これは `SpringBootLambdaContainerHandler` の中で対象エンドポイントへの
request をダミーで発行することで改善できる

```java
  // send a fake request to the handler to load classes ahead of time
  ApiGatewayRequestIdentity identity = new ApiGatewayRequestIdentity();
  identity.setApiKey(("foo"));
  identity.setAccountId("foo");
  identity.setAccessKey("foo");

  AwsProxyRequestContext reqCtx = new AwsProxyRequestContext();
  reqCtx.setPath("/pets");
  reqCtx.setStage("default");
  reqCtx.setAuthorizer(null);
  reqCtx.setIdentity(identity);

  AwsProxyRequest req = new AwsProxyRequest();
  req.setHttpMethod("GET");
  req.setPath("/pets");
  req.setBody("");
  req.setRequestContext(reqCtx);

  handler.proxy(req, null);

```

これにより snapstart を有効にした状態で restore から response まで 600ms 程度に短縮した

```
2024-03-26T08:56:21.347+09:00	RESTORE_START Runtime Version: java:21.v12 Runtime Version ARN: arn:aws:lambda:ap-northeast-1::runtime:f385d96535535bc14fd6f8593dcda7923aa783dae0d618b97cce1eea744d7a2b
2024-03-26T08:56:21.821+09:00	RESTORE_REPORT Restore Duration: 474.37 ms
2024-03-26T08:56:21.826+09:00	START RequestId: 5bb1aa9b-afdc-4245-ad85-a0f5480e4d38 Version: 5
2024-03-26T08:56:22.101+09:00	2024-03-25T23:56:22.101Z WARN 8 --- [ main] c.a.s.s.s.filter.CognitoIdentityFilter : Cognito identity id in request is null
2024-03-26T08:56:22.183+09:00	2024-03-25T23:56:22.182Z INFO 8 --- [ main] c.a.s.p.internal.LambdaContainerHandler : 49.98.158.6 null- null [25/03/2024:23:56:21Z] "GET /pets HTTP/1.1" 200 1050 "-" "curl/7.74.0" combined
2024-03-26T08:56:22.401+09:00	END RequestId: 5bb1aa9b-afdc-4245-ad85-a0f5480e4d38
2024-03-26T08:56:22.401+09:00	REPORT RequestId: 5bb1aa9b-afdc-4245-ad85-a0f5480e4d38 Duration: 575.43 ms Billed Duration: 695 ms Memory Size: 128 MB Max Memory Used: 128 MB Restore Durati
```
