# Wire™

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp/wire](https://github.com/wireapp/wire), and the apk of the latest release at [https://wire.com/en/download/](https://wire.com/en/download/).

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

If you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and cause that application to connect to our servers for any purposes, we refer to that resulting application as an “Open Source App”.  All Open Source Apps are subject to, and may only be used and/or commercialized in accordance with, the Terms of Use applicable to the Wire Application, which can be found at https://wire.com/legal/#terms.  Additionally, if you choose to build an Open Source App, certain restrictions apply, as follows:

a. You agree not to change the way the Open Source App connects and interacts with our servers; b. You agree not to weaken any of the security features of the Open Source App; c. You agree not to use our servers to store data for purposes other than the intended and original functionality of the Open Source App; d. You acknowledge that you are solely responsible for any and all updates to your Open Source App.

For clarity, if you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and do not cause that application to connect to our servers for any purposes, then that application will not be deemed an Open Source App and the foregoing will not apply to that application.

No license is granted to the Wire trademark and its associated logos, all of which will continue to be owned exclusively by Wire Swiss GmbH. Any use of the Wire trademark and/or its associated logos is expressly prohibited without the express prior written consent of Wire Swiss GmbH.

# github-app


## Features

Here's a list of features included in this project:

| Name                                     | Description                                |
|------------------------------------------|--------------------------------------------|
| /health                                  | Healthcheck endpoint returning HTTP OK 200. |
| /{conversation_id}/{conversation_domain} | Webhook endpoint.                          |

## Building & Running

To build or run the project, you can use the IDE Run configuration with environment variables.

| Project            | Environment Variables                                                                               |
|--------------------|-----------------------------------------------------------------------------------------------------|
| `GitHub App (this)` | Please check [EnvironmentVariables.kt](src/main/kotlin/com/wire/github/util/EnvironmentVariables.kt) |
| `Wire App SDK`     | Please check [Wire App SDK](https://github.com/wireapp/wire-apps-jvm-sdk)                           |

An example of this project environment variables:
```
GHAPP_API_HOST=https://127.0.0.1/github
GHAPP_SERVER_PORT=8083
GHAPP_REDIS_HOST=redis://redis
GHAPP_REDIS_PORT=6380
WIRE_SDK_API_HOST=https://nginz-https.chala.wire.link
WIRE_SDK_API_TOKEN=myApiToken
WIRE_SDK_APP_ID=f562e146-dec2-4d85-93c7-7132746b5cca
WIRE_SDK_CRYPTOGRAPHY_STORAGE_PASSWORD=myDummyPasswordmyDummyPassword01
```

## Deployment
Currently, we are only deploying in our Integrations VM.

When a proper release is done we will update this section.
