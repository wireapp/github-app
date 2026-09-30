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

| Name                                                   | Description                                      |
|--------------------------------------------------------|--------------------------------------------------|
| `/health`                                              | Healthcheck endpoint returning HTTP OK 200.      |
| `/{conversation_id}/{conversation_domain}`             | Receive signed GitHub repository webhooks.       |
| `/actions/{conversation_id}/{conversation_domain}`     | Receive workflow-authored GitHub Actions text.   |

### Repository webhook notifications

The existing webhook endpoint provides centralized notifications rendered by this service. It supports
these GitHub Actions-related repository webhook events:

| GitHub event | Supported action | Notification |
|--------------|------------------|--------------|
| Workflow runs (`workflow_run`) | `completed` | One summary for the completed workflow run. |
| Workflow jobs (`workflow_job`) | `completed` | One notification for each completed job in the workflow run. |

All completed conclusions are reported, including successful, failed, cancelled, skipped, and timed-out
results when GitHub provides them. Other actions, such as `requested`, `queued`, and `in_progress`, are
accepted by the endpoint but do not produce a Wire message.

Use `/github webhook help` in the Wire conversation for its URL, secret, and setup instructions.

### Workflow-authored GitHub Actions notifications

Use the reusable composite action to send workflow-composed text to a Wire conversation. The endpoint authenticates with the conversation secret already configured for GitHub webhooks. Store it as a GitHub Actions secret and do not print it in workflow logs. Empty or whitespace-only messages are rejected with `400`; a missing/invalid secret is rejected with `403`.

Use `/github actions help` in the Wire conversation to obtain the complete endpoint URL and shared
conversation secret. Store them as `WIRE_WEBHOOK_URL` and `WIRE_WEBHOOK_SECRET` respectively.

The action accepts these inputs:

| Input | Required | Description |
|-------|----------|-------------|
| `webhook-url` | Yes | Complete `/actions/{conversation_id}/{conversation_domain}` URL. |
| `secret` | Yes | Shared conversation secret used as the bearer token. |
| `text` | Yes | Complete message text to send without service-side formatting. |

```yaml
- name: Notify Wire on failure
  if: failure() || cancelled()
  uses: wireapp/github-app/.github/actions/notify-wire@v1
  with:
    webhook-url: ${{ secrets.WIRE_WEBHOOK_URL }}
    secret: ${{ secrets.WIRE_WEBHOOK_SECRET }}
    text: |
      CI failed for ${{ github.repository }} on `${{ github.ref_name }}`.
      Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
```

The action fails the workflow if the notification request fails; callers can opt into `continue-on-error: true` when notification delivery should not affect the job result. Pin the action to a released tag or commit SHA.

The workflow owns the notification condition and complete message text. For example, use `if: always()`
for an unconditional notification or `if: failure() || cancelled()` for failure-only notification.

The endpoint returns:

- `200 OK` after Wire accepts the message;
- `400 Bad Request` for an invalid conversation ID, malformed JSON, or blank text;
- `401 Unauthorized` for missing or malformed bearer authorization;
- `403 Forbidden` for an invalid token or a conversation without a stored token;
- `415 Unsupported Media Type` when the request is not JSON.

### Wire commands

| Command | Description |
|---------|-------------|
| `/github help` | List available commands and notification approaches. |
| `/github token` | Show or create the shared conversation token. |
| `/github webhook help` | Show repository webhook setup instructions. |
| `/github actions help` | Show the Actions endpoint, token usage, and reusable action example. |

### Conversation secret storage

The service stores one generated secret per qualified Wire conversation in Redis. The repository webhook
endpoint uses it for GitHub HMAC validation, while the Actions endpoint uses the same value as a bearer
token. `/github token`, `/github webhook help`, and `/github actions help` return the existing value or
create and store one when the conversation does not have one yet.

Treat this secret as a credential: anyone with the Actions endpoint and secret can post messages to the
conversation. Keep it in GitHub Actions secrets and avoid exposing it in workflow output.

### Reusable action releases

Publish immutable semantic-version tags such as `v1.0.0` for the reusable action. A moving `v1` tag may
also point to the latest compatible v1 release. Consumers that require reproducible workflows should pin
the action to an immutable version tag or commit SHA.

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
GHAPP_REDIS_URL=redis://username:password@host:port
WIRE_SDK_API_HOST=https://nginz-https.chala.wire.link
WIRE_SDK_API_TOKEN=myApiToken
WIRE_SDK_APP_ID=f562e146-dec2-4d85-93c7-7132746b5cca
WIRE_SDK_CRYPTOGRAPHY_STORAGE_PASSWORD=myDummyPasswordmyDummyPassword01
```

## Deployment
Currently, we are only deploying in our Integrations VM.

When a proper release is done we will update this section.
