# Firebase Realtime Database deserialises into these DTOs reflectively, so the
# field names must survive shrinking or every read comes back null in release.
-keepclassmembers class lk.ac.ucsc.scs3311.smarthome.data.remote.dto.** {
    *;
}
-keepattributes Signature
-keepattributes *Annotation*

# Room generates implementations at build time; keep the generated classes.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
