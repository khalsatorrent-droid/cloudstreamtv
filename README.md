# CloudStream TV

A fork of [CloudStream](https://github.com/recloudstream/cloudstream), tuned for the **Amazon Fire TV Stick 4K / 4K Plus / 4K Max** (Fire OS 8). It is the same app with the same extensions, made smoother on a TV stick: 4K HDR and Dolby Vision playback, remote-friendly navigation, and fixes that matter on a slower device.

> **🤖 Built with AI:** this fork and its Fire TV changes were made with the help of AI (Claude by Anthropic), which wrote the code, commits and pull requests in this repo. Every change is built and unit tested by the release workflow before it is published, but it has not been reviewed the way the official CloudStream code is.

**⚠️ Warning: By default, this app doesn't provide any video sources; you have to install extensions to add functionality to the app.**

<p align="left"><a href="https://github.com/khalsatorrent-droid/cloudstreamtv/releases/download/pre-release/cloudstream-firestick-4k.apk"><img src=".github/images/download-cloudstream-pre.png" alt="Download CloudStream TV for Fire TV" width=430/></a></p>

## Install on a Fire TV

1. On the Fire TV, install **Downloader** from the Amazon Appstore and allow it to install unknown apps in *Settings → My Fire TV → Developer options*. If Developer options is missing, open *Settings → My Fire TV → About* and click the device name 7 times.
2. In Downloader, open
   `https://github.com/khalsatorrent-droid/cloudstreamtv/releases/download/pre-release/cloudstream-firestick-4k.apk`
   and install it.
3. The app appears as **CloudStream TV**. If it is not on the home row, look under *Your Apps & Channels → See All*.

It installs as `com.lagradost.cloudstream3.firetv`, so it sits next to any official CloudStream app instead of replacing it, and it updates itself from the releases of this repo.

## What is different from CloudStream

**Playback**
- **4K remuxes without stutter**: video downloads with larger reads and over TCP (QUIC costs too much CPU on a stick), skips the app's web cache, and keeps at least 30 s loaded ahead as far as memory allows.
- **Frame rate matching** (*Settings → Player → Match frame rate*): *Off* (default), *On start* (switch once, keep the mode for the next video) or *On start and stop*. The switch happens behind a loading screen, menus over the player keep the mode, and the app measures the real frame rate of mkv files so a 23.976 fps file labelled 24 does not hold a frame every 40 seconds.
- **Tunneled playback** (*Settings → Player → Tunneled playback*, on by default): the TV hardware keeps video in step with the audio.
- **Dolby Vision profile 7** (UHD Blu-ray remuxes) plays as HDR10 on the TV, as Fire TV can only decode profiles 5 and 8. Profiles 5 and 8 stay Dolby Vision.
- **Audio**: when a film has both TrueHD/DTS-HD and Dolby Digital (Plus) in the same language, the Dolby track is chosen, so it goes to the TV or soundbar as is instead of being decoded on the stick's CPU.
- **Playback stats** (*Settings → Player → Show playback stats*): frame rate (stated and measured), TV refresh rate, dropped frames, buffer, audio, tunneling and memory, to find the cause of any stutter.

**On the TV home page**
- A **See all** tile at the start of every category row opens the whole category as a grid that keeps loading pages as you scroll with the remote.
- Lists lay out the next posters ahead of the remote and keep posters in memory, so holding the D-pad does not stutter.

**Fixes**
- Installs and shows up on the Fire TV launcher (own package name, a proper TV banner).
- Subtitle settings no longer stutter while a video is paused.
- Simkl sign-in on the TV uses a code and QR code only, no browser.
- Startup and settings screens are pre-compiled at install (baseline profile).

## Settings worth checking on the Fire TV

- *Settings → Display & Sounds → Display → Match Original Frame Rate*: on, if you use frame rate matching in the app.
- *Settings → Display & Sounds → Display → Dynamic Range Settings*: Adaptive. "Always HDR" makes normal video look washed out.
- After an update the app is a little slower until Fire OS compiles it while the TV is idle, usually overnight.

## For the maintainer of this fork

- **Releases**: every push to `master` builds the Fire TV apk (`.github/workflows/firestick-release.yml`) and replaces the `pre-release` release, including the fixed-name `cloudstream-firestick-4k.apk`.
- **Account keys**: Simkl, MAL and AniList sign-in need keys stored as repository secrets (*Settings → Secrets and variables → Actions*): `SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET`, `MAL_KEY`, `ANILIST_KEY`. For Simkl, create an app in the Simkl developer settings with the redirect URI `cloudstreamapp://simkl`. The build log says whether the Simkl key is set, without printing it.
- **Updating from CloudStream**: merge `recloudstream/cloudstream` `master` into this fork. The Fire TV changes live mostly in `ui/player` (`CS3IPlayer`, `FrameRateMatcher`, `VideoFrameRateProbe`, `UpdatedMatroskaExtractor`), the home adapters and `app/build.gradle.kts`.

Problems with this fork go to [this repo's issues](https://github.com/khalsatorrent-droid/cloudstreamtv/issues), not to the CloudStream team. Everything below is from the original CloudStream README.

---

## CloudStream

[![Discord](https://invidget.switchblade.xyz/5Hus6fM)](https://discord.gg/5Hus6fM)

## Table of Contents: 
+ [About Us:](#about_us)
+ [Installation Steps:](#install_rules)
+ [Contributing:](#contributing)
+ [Issues:](#issues)
  + [Bugs Reports:](#bug_report)
  + [Enhancement:](#enhancment)
+ [Extension Development:](#extensions)
+ [Language Support:](#languages)
+ [Further Sources](#contact_and_sources)


<a id="about_us"></a>

## About us: 

**CloudStream is a media center that prioritizes and emphasizes complete freedom and flexibility for users and developers.** 

CloudStream is an extension-based multimedia player with tracking support. There are extensions to view videos from: 

+ [Librevox (audio-books)](https://librivox.org/) 
+ [Youtube](https://www.youtube.com/)
+ [Twitch](https://www.twitch.tv/)
+ [iptv-org (A collection of publicly available IPTV (Internet Protocol television) channels from all over the world.)](https://github.com/iptv-org/iptv) 
+ [nginx](https://nginx.org/)
+ And more... 


**Please don't create illegal extensions or use any that host any copyrighted media.** For more details about our stance on the DMCA and EUCD, you can read about it on our organization: [reCloudStream](https://github.com/recloudstream)

#### Important Copyright Note: 

Our documentation is unmaintained and open to contributions; therefore, apps and sources, extensions in recommended sources, and recommended apps are not officially moderated or endorsed by CloudStream; if you or another copyright owner identify an extension that breaches your copyright, please let us know. 


#### Features:
+ **AdFree**, No ads whatsoever
+ No tracking/analytics
+ Bookmarks
+ Phone and TV support
+ Chromecast
+ Extension system for personal customization


<a id="install_rules"></a>

## Installation: 

Our documentation provides the steps to install and configure CloudStream for your streaming needs.

[Getting Started With CloudStream:](https://recloudstream.github.io/csdocs/)

<a id="contributing"></a>

## Contributing:
We **happily** accept any contributions to our project. To find out where you can start contributing towards the project, please look [at the CloudStream issues tab](https://github.com/recloudstream/cloudstream/issues)



<a id="issues"></a> 
 
### Issues: 
While we **actively** accept issues and pull requests, we do require you fill out an [template](https://github.com/recloudstream/cloudstream/issues/new/choose) for issues. These include the following:

<a id="bug_report"></a>

- [Bug Report Template: ](https://github.com/recloudstream/cloudstream/issues/new?assignees=&labels=bug&projects=&template=application-bug.yml)
  - For bug reports, we want as much info as possible, including your downloaded version of CloudeStream, device and updated version (if possible, current API),
    expected behavior of the program, and the actual behavior that the program did, most importantly we require clear, reproducible steps of the bug. If your bug can't be       reproduced, it is unlikely we'll work on your issue.
    
<a id="enhancment"></a>
  
- [Feature Request Template: ](https://github.com/recloudstream/cloudstream/issues/new?assignees=&labels=enhancement&projects=&template=feature-request.yml)
  - Before adding a feature request, please check to see if a feature request already has been requested.  


### Extensions:
 
**Further details on creating extensions for CloudStream are found in our documentation.**

[Guide: For Extension Developers](https://recloudstream.github.io/csdocs/devs/gettingstarted/) 

<a id="contact_and_sources"></a>

## Further Sources: 

As well as providing clear install steps, our [website](https://dweb.link/ipns/cloudstream.on.fleek.co/) includes a wide variety of other tools, such as: 
- [Troubleshooting](https://recloudstream.github.io/csdocs/troubleshooting/)
- [Further CloudStream Repositories](https://recloudstream.github.io/csdocs/repositories/) 
- Set-Up for other devices, such as:
  - [Android TV](https://recloudstream.github.io/csdocs/other-devices/tv/)
  - [Windows](https://recloudstream.github.io/csdocs/other-devices/windows/)
  - [Linux](https://recloudstream.github.io/csdocs/other-devices/linux/)
- And more...

<a id="languages"> </a>  

### Supported languages:

Even if you can't contribute to the code or documentation, we always look for those who can contribute to translation and language support. Your contribution is exceptionally appreciated; you can check our translation from the figure below. 

<a href="https://hosted.weblate.org/engage/cloudstream/">
  <img src="https://hosted.weblate.org/widgets/cloudstream/-/app/multi-auto.svg" alt="Translation status" />
</a>
