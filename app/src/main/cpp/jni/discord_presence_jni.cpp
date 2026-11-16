#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <utility>

#define DISCORDPP_IMPLEMENTATION
#include <discordpp.h>

namespace {

constexpr uint64_t kAudifyApplicationId = 1571895598062506015ULL;
std::unique_ptr<discordpp::Client> g_client;

std::optional<std::string> ToOptionalString(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::nullopt;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::nullopt;
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    if (result.empty()) return std::nullopt;
    return result;
}

discordpp::ActivityTypes ToActivityType(jint value) {
    switch (value) {
        case 1: return discordpp::ActivityTypes::Playing;
        case 2: return discordpp::ActivityTypes::Watching;
        case 3: return discordpp::ActivityTypes::Competing;
        default: return discordpp::ActivityTypes::Listening;
    }
}

void AddButton(
    JNIEnv* env,
    discordpp::Activity& activity,
    jstring label,
    jstring url
) {
    auto buttonLabel = ToOptionalString(env, label);
    auto buttonUrl = ToOptionalString(env, url);
    if (!buttonLabel || !buttonUrl) return;
    discordpp::ActivityButton button;
    button.SetLabel(*buttonLabel);
    button.SetUrl(*buttonUrl);
    activity.AddButton(std::move(button));
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_music_audify_data_discord_DiscordSocialPresence_nativeInitialize(
    JNIEnv*, jobject, jlong applicationId
) {
    if (g_client) return JNI_TRUE;
    try {
        g_client = std::make_unique<discordpp::Client>();
        g_client->SetApplicationId(
            applicationId > 0 ? static_cast<uint64_t>(applicationId) : kAudifyApplicationId
        );
        return JNI_TRUE;
    } catch (...) {
        g_client.reset();
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_audify_data_discord_DiscordSocialPresence_nativeUpdate(
    JNIEnv* env,
    jobject,
    jstring details,
    jstring state,
    jstring imageUrl,
    jstring imageTooltip,
    jstring detailsUrl,
    jlong startMs,
    jlong endMs,
    jboolean showArtwork,
    jboolean showElapsedTime,
    jint activityType,
    jboolean useDetails,
    jstring button1Label,
    jstring button1Url,
    jstring button2Label,
    jstring button2Url
) {
    if (!g_client) return;

    discordpp::Activity activity;
    activity.SetType(ToActivityType(activityType));
    activity.SetDetails(ToOptionalString(env, details));
    activity.SetState(ToOptionalString(env, state));
    activity.SetDetailsUrl(ToOptionalString(env, detailsUrl));
    activity.SetStatusDisplayType(
        useDetails == JNI_TRUE
            ? discordpp::StatusDisplayTypes::Details
            : discordpp::StatusDisplayTypes::State
    );

    auto image = showArtwork == JNI_TRUE ? ToOptionalString(env, imageUrl) : std::nullopt;
    auto tooltip = ToOptionalString(env, imageTooltip);
    if (image || tooltip) {
        discordpp::ActivityAssets assets;
        assets.SetLargeImage(std::move(image));
        assets.SetLargeText(std::move(tooltip));
        activity.SetAssets(std::move(assets));
    }

    if (showElapsedTime == JNI_TRUE && (startMs > 0 || endMs > 0)) {
        discordpp::ActivityTimestamps timestamps;
        if (startMs > 0) timestamps.SetStart(static_cast<uint64_t>(startMs));
        if (endMs > 0) timestamps.SetEnd(static_cast<uint64_t>(endMs));
        activity.SetTimestamps(std::move(timestamps));
    }

    AddButton(env, activity, button1Label, button1Url);
    AddButton(env, activity, button2Label, button2Url);

    // Do not call Client::Connect(): on Android 1.10+ this uses the signed-in
    // Discord app's unauthenticated RPC transport and requires no user token.
    g_client->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        if (result.Successful()) {
            __android_log_print(ANDROID_LOG_INFO, "AudifyDiscord", "Discord accepted Rich Presence update");
        } else {
            const std::string error = result.Error();
            __android_log_print(ANDROID_LOG_WARN, "AudifyDiscord", "Presence update failed: %s", error.c_str());
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_audify_data_discord_DiscordSocialPresence_nativeClear(JNIEnv*, jobject) {
    if (g_client) g_client->ClearRichPresence();
}

extern "C" JNIEXPORT void JNICALL
Java_com_music_audify_data_discord_DiscordSocialPresence_nativeRunCallbacks(JNIEnv*, jobject) {
    discordpp::RunCallbacks();
}
