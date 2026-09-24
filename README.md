# ViaFabricPlus Bedrock

Addon for [ViaFabricPlus](https://github.com/ViaVersion/ViaFabricPlus) that adds Minecraft: Bedrock Edition support back
to the mod.

## Usage

1. Install [ViaFabricPlus](https://modrinth.com/mod/viafabricplus) 5.1.0 or newer and this addon.
2. Open the ViaFabricPlus screen and select the Bedrock version.
3. For online mode servers, Realms, and friends' worlds, open the settings, switch to the `Bedrock` tab and click
   `Account for Bedrock Edition`. Your browser opens for the Microsoft login, and the entry then shows the name of the
   account that is signed in.

Log in once. The same account is used for every online mode Bedrock
server. `Bedrock Realms` and `Bedrock Friends` in the ViaFabricPlus screen stay disabled until an account is set.

Open `Bedrock Friends` to see your Xbox friends and their online status. The `Requests` tab lets you accept, decline,
or cancel friend requests. Use `Find players` to search by Gamertag and send a request. Select a player and click
`Profile` to see their Xbox details, or `Remove friend` to remove them.

The `Worlds` tab lists joinable worlds hosted by Xbox friends. Select a world and click `Join world`. You can also
select a friend with a joinable world from the `Friends` tab. The screen checks the world's protocol before joining.
Use `Refresh` to update the lists.

To join a NetherNet server directly, select the Bedrock version and enter one of these address forms:

| Address | Signaling service |
| --- | --- |
| `nethernet://host[:port]` | HTTP, default port 19132 |
| `nethernet-lan://host[:port]` | LAN discovery, default port 7551 |
| `nethernet-xbox://network-id` | Xbox WebSocket |
| `nethernet-xbox-json-rpc://network-id` | Xbox JSON-RPC |

Xbox signaling needs a Bedrock account. HTTP and LAN signaling can connect without one if the server permits it.

The remaining `Bedrock` settings control whether the default Bedrock port is filled in automatically and whether
ViaBedrock's experimental features are enabled.

## Gradle

The mod is published to the ViaVersion repository as `com.viaversion:viafabricplus-bedrock`.

```kotlin
repositories {
    maven("https://repo.viaversion.com")
}

dependencies {
    // Replace it with latest release
    runtimeOnly("com.viaversion:viafabricplus-bedrock:x.x.x")
}
```

## Links

- ViaFabricPlus: https://github.com/ViaVersion/ViaFabricPlus
- ViaBedrock: https://github.com/RaphiMC/ViaBedrock

## Contact

- Issues: https://github.com/florianreuth/viafabricplus-bedrock/issues
- Discord: https://florianreuth.de/discord
