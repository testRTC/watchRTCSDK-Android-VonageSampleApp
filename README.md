# WatchRTC Vonage Android sample application
This is the WatchRTC Android Vonage sample application.

WatchRTC
-----------
WatchRTC integration code will located in `MainActivity.kt` file and WatchRTC sdk init function is `initWatchRTCSDK()`

# Basic Video Chat

This application provides a completed version of the OpenTok [Basic Video Chat tutorial](https://tokbox.com/developer/tutorials/android/) for Android (differing only in some additional validation checks). 
Upon deploying this sample application, you should be able to have two-way audio and video communication using OpenTok.

Main features:
* Connect to an OpenTok session
* Publish an audio-video stream to the session
* Subscribe to another client's audio-video stream


Step to use
-----------
To use this application:
- Generate WatchRTC_api_key and update in `WatchRTCConfig`.
- Generate api_key, session_id, token using [tokbox account](https://tokbox.com/account/user/signup). 
- Update this these credential into `OpenTokConfig.kt` class
- Run application into two phone and enter the same room-id in both phone and start call
- Press back button from both phone and go to WatchRTC portal to check call stats information.

Further Reading
-----------
- Read more about [OpenTok Android SDK](https://tokbox.com/developer/sdks/android/)
- [WatchRTC Sample application](https://github.com/testRTC/watchRTCSDK-Android-SampleApp)
- [WatchRTC Twilio Sample application](https://github.com/testRTC/watchRTCSDK-Android-TwilioSampleApp)
