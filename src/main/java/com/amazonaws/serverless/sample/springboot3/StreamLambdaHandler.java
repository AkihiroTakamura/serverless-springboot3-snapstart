package com.amazonaws.serverless.sample.springboot3;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.testutils.Timer;
import com.amazonaws.serverless.proxy.model.ApiGatewayRequestIdentity;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyRequestContext;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.serverless.sample.springboot3.filter.CognitoIdentityFilter;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

public class StreamLambdaHandler implements RequestStreamHandler {
  private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;
  static {
    try {
      handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(Application.class);

      // we use the onStartup method of the handler to register our custom filter
      handler.onStartup(servletContext -> {
        FilterRegistration.Dynamic registration = servletContext.addFilter("CognitoIdentityFilter",
            CognitoIdentityFilter.class);
        registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
      });

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

    } catch (ContainerInitializationException e) {
      // if we fail here. We re-throw the exception to force another cold start
      e.printStackTrace();
      throw new RuntimeException("Could not initialize Spring Boot application", e);
    }
  }

  public StreamLambdaHandler() {
    // we enable the timer for debugging. This SHOULD NOT be enabled in production.
    Timer.enable();
  }

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
      throws IOException {
    handler.proxyStream(inputStream, outputStream, context);
  }
}
