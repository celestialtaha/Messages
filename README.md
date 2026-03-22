# Messages (Phone App)

Messages is the phone app in this project and the sync source for the Wear OS companion (`Wessage`).

Built for daily use:

- clean and familiar messaging UI
- reliable local sync to watch
- privacy-first operation with no extra cloud account

## For Most Users

Get the latest APK from Releases:

- https://github.com/celestialtaha/Messages/releases

Install on your phone, grant SMS/contacts/notifications permissions, and open the app once before opening the watch app.

## Pairing With Wessage (Watch)

1. Install this app on phone.
2. Install `Wessage` on your Wear OS watch:
   - https://github.com/celestialtaha/Wessage/releases
3. Make sure phone and watch are paired and Bluetooth is on.
4. Open phone app, then open watch app.
5. Messages should appear on watch after initial sync.

## Privacy

- Core sync is phone <-> watch (Wear OS data layer).
- No dedicated backend account is required.
- Internet is not required for normal nearby sync after pairing.

## Troubleshooting

- Watch shows no threads:
  - Open both apps once.
  - Verify watch pairing in Wear OS app.
  - Keep Bluetooth enabled.
- Delayed first load:
  - Initial sync can take longer than incremental updates.

## Release Process (Maintainers)

1. Update version in `gradle.properties` (`VERSION_NAME`, `VERSION_CODE`).
2. Build release APK(s).
3. Tag and push:

```bash
git tag v8.2.2
git push origin v8.2.2
```

4. Publish a GitHub Release and attach APK artifacts.

## Credits

Based on:

- https://github.com/SimpleMobileTools/Simple-SMS-Messenger
- https://github.com/FossifyOrg/Messages
