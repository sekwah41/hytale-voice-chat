# Contributing

Thanks for helping improve Hytale Voice Chat. Please keep changes focused and consistent with the existing project style.

## Quick Start

1. Clone the repo.
2. Work in a branch.
3. Keep changes minimal and scoped to the task.

## Conventional Commits

- Use Conventional Commits for commit messages (e.g. `feat: add distance falloff`).

The following tags are commonly used:
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `tweak`: A small adjustment to an existing feature
- `chore`: Changes to the build process or auxiliary tools/libraries

At some point we will be using these to generate release changelogs.

## Frontend Development

The frontend lives in `hytale-voice-chat-frontend/`.

If you add new assets, place them in `hytale-voice-chat-frontend/public/`.

2. Set `VoiceChatDevForwardingEnabled` to `true` in `VoiceChat.json`.
2. Leave `VoiceChatPublicUrl` blank.
3. In `hytale-voice-chat-frontend/`, run:
   - `yarn` to install dependencies
   - `yarn dev` to start the frontend

You can also add `?debugaudio` to the frontend URL to play with some extra buttons for testing filters or streaming your own mic or a song on loop at a location, though this will be purely client side and designed to help test the audio pipelined.



## Code Style

- Use existing formatting and conventions.
- Keep comments concise and only when needed.
- Prefer small, targeted changes over large refactors.

## Backend
- Java sources live in `src/main/java/`.

## Pull Requests
- Describe what changed and why.
- Explain how to test/verify that the changes work if its not obvious.
- Note any breaking changes or follow-up work that may be needed.
