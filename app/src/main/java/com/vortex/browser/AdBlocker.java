package com.vortex.browser;

import java.util.HashSet;
import java.util.Set;

/**
 * Vortex Browser: Ad and tracker blocking engine.
 * Uses domain-based filtering for fast blocking.
 */
public class AdBlocker {

    // Ad domains
    private static final Set<String> AD_DOMAINS = new HashSet<>();
    private static final Set<String> TRACKER_DOMAINS = new HashSet<>();

    static {
        // Major ad networks
        AD_DOMAINS.add("doubleclick.net");
        AD_DOMAINS.add("googlesyndication.com");
        AD_DOMAINS.add("googleadservices.com");
        AD_DOMAINS.add("google-analytics.com");
        AD_DOMAINS.add("googletagmanager.com");
        AD_DOMAINS.add("googletagservices.com");
        AD_DOMAINS.add("adservice.google.com");
        AD_DOMAINS.add("pagead2.googlesyndication.com");
        AD_DOMAINS.add("adnxs.com");
        AD_DOMAINS.add("ads.yahoo.com");
        AD_DOMAINS.add("ad.doubleclick.net");
        AD_DOMAINS.add("static.ads-twitter.com");
        AD_DOMAINS.add("ads-api.twitter.com");
        AD_DOMAINS.add("advertising.com");
        AD_DOMAINS.add("adobedtm.com");
        AD_DOMAINS.add("adsrvr.org");
        AD_DOMAINS.add("adform.net");
        AD_DOMAINS.add("serving-sys.com");
        AD_DOMAINS.add("outbrain.com");
        AD_DOMAINS.add("taboola.com");
        AD_DOMAINS.add("revenuehits.com");
        AD_DOMAINS.add("popads.net");
        AD_DOMAINS.add("popcash.net");
        AD_DOMAINS.add("adcolony.com");
        AD_DOMAINS.add("unity3d.com");
        AD_DOMAINS.add("unityads.unity3d.com");
        AD_DOMAINS.add("applovin.com");
        AD_DOMAINS.add("mopub.com");
        AD_DOMAINS.add("inmobi.com");
        AD_DOMAINS.add("chartboost.com");
        AD_DOMAINS.add("admob.com");
        AD_DOMAINS.add("moatads.com");
        AD_DOMAINS.add("amazon-adsystem.com");
        AD_DOMAINS.add("media.net");
        AD_DOMAINS.add("criteo.com");
        AD_DOMAINS.add("criteo.net");
        AD_DOMAINS.add("rubiconproject.com");
        AD_DOMAINS.add("pubmatic.com");
        AD_DOMAINS.add("openx.net");
        AD_DOMAINS.add("smartadserver.com");
        AD_DOMAINS.add("smaato.net");
        AD_DOMAINS.add("yieldmanager.com");
        AD_DOMAINS.add("zedo.com");

        // Tracker domains
        TRACKER_DOMAINS.add("facebook.com/tr");
        TRACKER_DOMAINS.add("connect.facebook.net");
        TRACKER_DOMAINS.add("pixel.facebook.com");
        TRACKER_DOMAINS.add("analytics.twitter.com");
        TRACKER_DOMAINS.add("t.co");
        TRACKER_DOMAINS.add("scorecardresearch.com");
        TRACKER_DOMAINS.add("quantserve.com");
        TRACKER_DOMAINS.add("segment.io");
        TRACKER_DOMAINS.add("segment.com");
        TRACKER_DOMAINS.add("mixpanel.com");
        TRACKER_DOMAINS.add("amplitude.com");
        TRACKER_DOMAINS.add("hotjar.com");
        TRACKER_DOMAINS.add("fullstory.com");
        TRACKER_DOMAINS.add("mouseflow.com");
        TRACKER_DOMAINS.add("clarity.ms");
        TRACKER_DOMAINS.add("crazyegg.com");
        TRACKER_DOMAINS.add("optimizely.com");
        TRACKER_DOMAINS.add("newrelic.com");
        TRACKER_DOMAINS.add("nr-data.net");
        TRACKER_DOMAINS.add("sentry.io");
        TRACKER_DOMAINS.add("bugsnag.com");
        TRACKER_DOMAINS.add("branch.io");
        TRACKER_DOMAINS.add("appsflyer.com");
        TRACKER_DOMAINS.add("adjust.com");
        TRACKER_DOMAINS.add("kochava.com");
        TRACKER_DOMAINS.add("app-measurement.com");
        TRACKER_DOMAINS.add("firebase-settings.crashlytics.com");
        TRACKER_DOMAINS.add("demdex.net");
        TRACKER_DOMAINS.add("omtrdc.net");
        TRACKER_DOMAINS.add("2o7.net");
        TRACKER_DOMAINS.add("bluekai.com");
        TRACKER_DOMAINS.add("exelator.com");
        TRACKER_DOMAINS.add("tapad.com");
        TRACKER_DOMAINS.add("rlcdn.com");
        TRACKER_DOMAINS.add("casalemedia.com");
        TRACKER_DOMAINS.add("mathtag.com");
    }

    public static boolean isAd(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : AD_DOMAINS) {
            if (lower.contains(domain)) return true;
        }
        // Pattern-based blocking
        if (lower.contains("/ads/") || lower.contains("/ad/") ||
            lower.contains("banner") && lower.contains(".jpg") ||
            lower.contains("pop-up") || lower.contains("popup") && lower.contains("ad")) {
            return true;
        }
        return false;
    }

    public static boolean isTracker(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : TRACKER_DOMAINS) {
            if (lower.contains(domain)) return true;
        }
        // Pattern-based tracker blocking
        if (lower.contains("tracking") || lower.contains("telemetry") ||
            lower.contains("beacon") || lower.contains("pixel.") ||
            lower.contains("collect?") && lower.contains("analytics")) {
            return true;
        }
        return false;
    }

    // Fingerprint protection JavaScript injection
    public static final String FINGERPRINT_PROTECTION_JS = "(function() {"
        + "if (window._vortexFpProtected) return;"
        + "window._vortexFpProtected = true;"

        // Canvas fingerprint randomization
        + "var origToDataURL = HTMLCanvasElement.prototype.toDataURL;"
        + "var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;"
        + "HTMLCanvasElement.prototype.toDataURL = function() {"
        + "  try {"
        + "    var ctx = this.getContext('2d');"
        + "    if (ctx) {"
        + "      var img = origGetImageData.call(ctx, 0, 0, this.width, this.height);"
        + "      for (var i = 0; i < img.data.length; i += 4) {"
        + "        img.data[i] ^= (Math.random() * 2) | 0;"
        + "      }"
        + "      ctx.putImageData(img, 0, 0);"
        + "    }"
        + "  } catch(e) {}"
        + "  return origToDataURL.apply(this, arguments);"
        + "};"

        // WebGL vendor/renderer masking
        + "var origGetParam = WebGLRenderingContext.prototype.getParameter;"
        + "WebGLRenderingContext.prototype.getParameter = function(p) {"
        + "  if (p === 37445) return 'Vortex Graphics';"
        + "  if (p === 37446) return 'Vortex GPU';"
        + "  return origGetParam.call(this, p);"
        + "};"

        // AudioContext fingerprint noise
        + "if (window.AudioContext || window.webkitAudioContext) {"
        + "  var AC = window.AudioContext || window.webkitAudioContext;"
        + "  var origCreateOsc = AC.prototype.createOscillator;"
        + "  AC.prototype.createOscillator = function() {"
        + "    var osc = origCreateOsc.call(this);"
        + "    var origConn = osc.connect.bind(osc);"
        + "    osc.connect = function(dest) {"
        + "      if (dest instanceof AnalyserNode) {"
        + "        var g = this.context.createGain();"
        + "        g.gain.value = 0.999 + Math.random() * 0.002;"
        + "        origConn(g); g.connect(dest); return g;"
        + "      }"
        + "      return origConn(dest);"
        + "    };"
        + "    return osc;"
        + "  };"
        + "}"

        // Screen resolution masking
        + "try {"
        + "  Object.defineProperty(screen, 'width', { get: function() { return screen.availWidth; }});"
        + "  Object.defineProperty(screen, 'height', { get: function() { return screen.availHeight; }});"
        + "} catch(e) {}"

        + "})();";
}
