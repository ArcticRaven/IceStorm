package dev.arctic.icestorm.corelib.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Objects;

/**
 * Color utilities for perceptual interpolation and conversion using OkLab.
 *
 * <p>This class provides conversions between sRGB and OkLab, along with helpers for working
 * with hex color strings commonly used in CoreLib tag parsing.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OkColorUtil {

    /**
     * Converts an sRGB channel in [0..255] to linear light in [0..1].
     *
     * @param channel8 sRGB channel value
     * @return linear channel value
     */
    public static double srgb8ToLinear(int channel8) {
        double channel = clamp01(channel8 / 255.0);
        if (channel <= 0.04045) {
            return channel / 12.92;
        }
        return Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /**
     * Converts a linear channel in [0..1] to sRGB channel in [0..255].
     *
     * @param linear linear channel value
     * @return sRGB channel value
     */
    public static int linearToSrgb8(double linear) {
        double channel = clamp01(linear);

        double srgb;
        if (channel <= 0.0031308) {
            srgb = 12.92 * channel;
        } else {
            srgb = 1.055 * Math.pow(channel, 1.0 / 2.4) - 0.055;
        }

        return clamp255((int) Math.round(srgb * 255.0));
    }

    /**
     * Converts sRGB (8-bit) to OkLab.
     *
     * @param r8 red channel [0..255]
     * @param g8 green channel [0..255]
     * @param b8 blue channel [0..255]
     * @return OkLab representation
     */
    public static OkLab srgbToOkLab(int r8, int g8, int b8) {
        double r = srgb8ToLinear(r8);
        double g = srgb8ToLinear(g8);
        double b = srgb8ToLinear(b8);

        // Linear sRGB -> LMS
        double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

        double lCbrt = Math.cbrt(l);
        double mCbrt = Math.cbrt(m);
        double sCbrt = Math.cbrt(s);

        double L = 0.2104542553 * lCbrt + 0.7936177850 * mCbrt - 0.0040720468 * sCbrt;
        double a = 1.9779984951 * lCbrt - 2.4285922050 * mCbrt + 0.4505937099 * sCbrt;
        double b2 = 0.0259040371 * lCbrt + 0.7827717662 * mCbrt - 0.8086757660 * sCbrt;

        return new OkLab(L, a, b2);
    }

    /**
     * Converts OkLab to sRGB (8-bit).
     *
     * @param L OkLab lightness
     * @param a OkLab a axis
     * @param b OkLab b axis
     * @return sRGB channels as [r, g, b]
     */
    public static int[] okLabToSrgb(double L, double a, double b) {
        // OkLab -> LMS
        double lCbrt = L + 0.3963377774 * a + 0.2158037573 * b;
        double mCbrt = L - 0.1055613458 * a - 0.0638541728 * b;
        double sCbrt = L - 0.0894841775 * a - 1.2914855480 * b;

        double l = lCbrt * lCbrt * lCbrt;
        double m = mCbrt * mCbrt * mCbrt;
        double s = sCbrt * sCbrt * sCbrt;

        // LMS -> linear sRGB
        double rLin = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        int r8 = linearToSrgb8(rLin);
        int g8 = linearToSrgb8(gLin);
        int b8 = linearToSrgb8(bLin);

        return new int[] { r8, g8, b8 };
    }

    /**
     * Parses {@code #RRGGBB} or {@code #RRGGBBAA} into an {@link RgbaColor}.
     *
     * @param hex hex color string
     * @return parsed RGBA
     */
    public static RgbaColor parseHex(String hex) {
        Objects.requireNonNull(hex, "hex");

        String value = hex.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        }

        if (value.length() != 6 && value.length() != 8) {
            throw new IllegalArgumentException("Invalid hex color: #" + value);
        }

        int r = Integer.parseInt(value.substring(0, 2), 16);
        int g = Integer.parseInt(value.substring(2, 4), 16);
        int b = Integer.parseInt(value.substring(4, 6), 16);

        if (value.length() == 8) {
            int a = Integer.parseInt(value.substring(6, 8), 16);
            return new RgbaColor(r, g, b, a, true);
        }

        return new RgbaColor(r, g, b, 255, false);
    }

    /**
     * Encodes an {@link RgbaColor} into {@code #RRGGBB} or {@code #RRGGBBAA}.
     *
     * @param color color to encode
     * @param includeAlpha whether to include alpha bytes
     * @return hex string
     */
    public static String toHex(RgbaColor color, boolean includeAlpha) {
        Objects.requireNonNull(color, "color");
        String rr = to2(color.red);
        String gg = to2(color.green);
        String bb = to2(color.blue);

        if (includeAlpha) {
            return "#" + rr + gg + bb + to2(color.alpha);
        }

        return "#" + rr + gg + bb;
    }

    /**
     * OkLab triple.
     *
     * @param lightness lightness component
     * @param axisA a axis component
     * @param axisB b axis component
     */
    public record OkLab(double lightness, double axisA, double axisB) {}

    /**
     * RGBA container for 8-bit channels.
     *
     * @param red red channel
     * @param green green channel
     * @param blue blue channel
     * @param alpha alpha channel
     * @param hasAlpha whether the original input included alpha
     */
    public record RgbaColor(int red, int green, int blue, int alpha, boolean hasAlpha) {}

    private static String to2(int value) {
        String s = Integer.toHexString(clamp255(value));
        return (s.length() == 1) ? ("0" + s) : s;
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}
