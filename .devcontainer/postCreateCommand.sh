# git
git config --global --add safe.directory /workspace

# install git-remote-codecommit
pip install git-remote-codecommit

# install sam
arch="$(uname -m)"
if [[ "${arch}" == "aarch64" ]]
then
  curl -L https://github.com/aws/aws-sam-cli/releases/latest/download/aws-sam-cli-linux-arm64.zip -o ~/aws-sam-cli-linux.zip
else
  curl -L https://github.com/aws/aws-sam-cli/releases/latest/download/aws-sam-cli-linux-x86_64.zip -o ~/aws-sam-cli-linux.zip
fi

unzip ~/aws-sam-cli-linux.zip -d ~/sam-installation
sudo ~/sam-installation/install
