# Hytale Voice Chat

An open-source proximity voice chat mod which only needs a browser page to be kept open in the background.

By default, I have a turn server hosted on a small VPS for public use, so you can just use this mod as is without any extra setup.
However, if you want to set up your own TURN server for better performance or privacy, you can point the mod to your own server in the config file.

If my TURN server becomes overloaded, you can switch to peer-to-peer mode or set up your own TURN server.
Though if the mod becomes popular enough, I may look into getting a better server or switch the default to Peer to Peer with TURN fallback.

## Features
 * No additional software needed, just a web browser.
 * 3D Proximity based voice chat.
 * Clients remember their settings between sessions so you don't have to type the command each time, just re-open the page.
 * Configurable connection methods (TURN server, Peer to Peer, or both (uses TURN as a fallback)).

## What will happen when Hytale adds their own voice chat?
Ideally all of these voice chat mods will become obsolete relatively soon once Hytale adds their own built-in voice chat system.
Though I plan to possibly replace this mod with one which adds extra capabilities and features to the built-in voice chat system instead.

## Usage
Just type /voice in the chat to get the link to the voice chat web page.

By default, it will come up with a warning saying the connection is insecure as it uses a self-signed certificate.
For now users can just dismiss this and continue to the page then it will not show up again.
We will be adding support for adding your own SSL certificate in the future.


## Sponsorship
If you need a server and would like to support my projects, you can get a server through my sponsor!
[![Nodecraft](https://dynamic-assets.nodecraft.com/signedurl/7vokBAexKazElnMZ1Q/image.jpg?modifications=W3sibmFtZSI6ImNvZGUiLCJ0ZXh0Ijoic2Vrd2FoIn1d&s=e595e2e9e069606792778083d8566a79481782e0ec34a6ff4f5de9052fec1871)](https://nodecraft.com/r/sekwah)

## Setup
1. Download the latest release.
2. Place the mod in your server's `mods` folder.
3. Start the server.
4. Change the config settings in `mods/Sekwah_VoiceChat/VoiceChat.json` to be the public ip of your server.
5. Enter `/plugin reload Sekwah:VoiceChat` in game or restart the server to apply the config changes.
6. You are done!

Custom SSL certificates are not yet supported but will be in the future.

## Connection Configurations
There are two main ways to configure the voice chat connections between players:

### WebRTC via TURN Server (Default)
I am currently hosting a free TURN server on a small VPS for public use, so you can just use this mod as is without any extra setup.
This option uses a TURN server to relay the voice data between players.
PROS:
* Works for all players regardless of network setup.
* Players' IP addresses are not exposed to each other.
CONS:
* Requires setting up a TURN server if mine is overloaded.

### WebRTC Peer to Peer
This option is in case my server is getting overloaded, and you are not able to set up your own TURN server.
It requires very little server resources as the voice data is sent directly between players.
PROS:
* Low server resource usage as voice data is sent directly between players.
* Super low latency voice chat.
  CONS:
* May not work for all players depending on their network setup.
* Exposes players' IP addresses to each other. (Should be fine for small trusted servers)

### WebRTC with backup TURN Server
This option tries to connect players directly first, and if that fails, it falls back to using the TURN server.
This is a good compromise between the two options above.
PROS:
* Low latency voice chat when direct connection is possible.
* Works for all players regardless of network setup.
CONS:
* Requires setting up a TURN server if mine is overloaded.


### Data through the server. (Future)
As audio data is very small, it should be possible to route the audio data through the game server itself with little issue especially for small player counts.

## Plans
Here are examples of features I have planned, to suggest more feel free to open an issue, or start a discussion on [GitHub](https://github.com/sekwah41/hytale-voice-chat) or join my [discord server](https://discord.sekwah.com).
Initially this was just a test mod, though I do plan to add more features in the future such as:
- Voice API for other mods to control voice.
- Custom SSL certificate support.
- Push to talk support.
- In game voice indicator showing who is talking.
- Support for custom TURN servers to proxy the voice chat.
- In game UI to configure various voice chat settings.
- Audio effects such as reverb based on environment.
- Explore support with the online share codes for personal words.

## Contributions
If you would like to contribute to the development of the mod, please feel free to fork the repository and submit a pull request. Any help is greatly appreciated!
