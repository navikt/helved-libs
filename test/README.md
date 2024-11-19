# Running the MQ Docker image in a testcontainer on M3 macbooks
The `ibmcom:mq` Docker image doesn't work on macbooks with the M3 chip. In order to run tests that are dependent on MQ, you'll need to use a different
Docker image in the testcontainer. There is a `LocalMQContainerForM3` class in this package which can be used, but it requires that you build the `ibm-mqadvanced-server-dev` image locally.

Clone the [mq-container repository](https://github.com/ibm-messaging/mq-container) and follow the [build documentation](https://github.com/ibm-messaging/mq-container/blob/master/docs/building.md#building-a-developer-image) for the developer image. 
If you're using Colima, make sure that you have `docker buildx` installed. Buildx can be installed either [manually](https://github.com/docker/buildx?tab=readme-ov-file#manual-download) or using Homebrew.

Once you've built the developer image, you can swap the existing `MQContainer` class in your tests with the `LocalMQContainerForM3` class.
Remember not to push your changes in to remote as the `ibm-mqadvanced-server-dev` won't work on Github Actions. 
