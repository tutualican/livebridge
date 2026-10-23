# LiveBridge — kişisel fork notları

Bu fork `appsfolder/livebridge` (v2.2.1, GPL-3.0) üzerine kişisel kullanım için yapılan
değişiklikleri içerir. Upstream PR süreci hedeflenmez.

## Bu forkta eklenenler

- **Türkçe parser sözlüğü** (`android/app/src/main/assets/liveupdate_dictionary_tr.json`)
  ve Türkçe metinlerde büyük/küçük harf (`İ`/`ı`) düzeltmesi. Upstream PR #141 temel alındı.
- **Şarj Live Update** (`ChargingStatusNotifier`): cihaz şarj olurken pil yüzdesini ve
  kalan şarj süresini canlı bildirim olarak gösterir. Ayarlar → Uygulama yapılandırması
  altından açılıp kapatılabilir (varsayılan: açık).

## Gereksinimler

- Flutter 3.35.7
- Android SDK 36 (minSdk 36)
- JDK 17 veya üzeri

## Derleme

Debug APK (imza gerekmez):

```
flutter pub get
flutter build apk --debug
```

Release APK için `android/key.properties` ve bir keystore gerekir:

```
storeFile=../keystore/livebridge.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```
flutter build apk --release
```

## Notlar

- İlk kurulumda resmi LiveBridge APK'sı ile imza çakışması olur; ayarları JSON olarak
  dışa aktarıp uygulamayı kaldırmak, yeni APK'yı kurup geri yüklemek gerekir.
- Uygulama içi güncelleme kontrolü upstream GitHub sürümlerine bakar; kişisel sürümde
  Ayarlar → Güncelleme ekranından kapatılabilir.
- APK'yı dağıtırsanız GPL-3.0 koşulları (kaynak + `NOTICE` + "LiveBridge tabanlı" ibaresi)
  geçerlidir.
