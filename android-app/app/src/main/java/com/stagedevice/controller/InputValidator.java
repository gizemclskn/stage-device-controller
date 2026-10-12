package com.stagedevice.controller;

/**
 * IP adresi ve port girişlerini doğrular.
 * Android'e bağlı değildir, bu yüzden kolayca birim testle sınanabilir.
 */
public final class InputValidator {

    public enum IpResult { OK, EMPTY, INVALID }

    public enum PortResult { OK, EMPTY, INVALID, OUT_OF_RANGE }

    private InputValidator() {
    }

    /** IPv4 adresi: noktayla ayrılmış 4 sayı, her biri 0-255, başında gereksiz sıfır yok. */
    public static IpResult validateIp(String text) {
        if (text == null) {
            return IpResult.EMPTY;
        }
        String ip = text.trim();
        if (ip.isEmpty()) {
            return IpResult.EMPTY;
        }
        // -1: sondaki boş parçaları da koru ("1.2.3.4." geçersiz sayılsın)
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return IpResult.INVALID;
        }
        for (String part : parts) {
            if (!isValidOctet(part)) {
                return IpResult.INVALID;
            }
        }
        return IpResult.OK;
    }

    private static boolean isValidOctet(String part) {
        if (part.isEmpty() || part.length() > 3) {
            return false;
        }
        if (part.length() > 1 && part.charAt(0) == '0') {
            return false; // "010" gibi başında sıfır olan sayılar kabul edilmez
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return Integer.parseInt(part) <= 255;
    }

    /** Port: yalnızca rakam, 1-65535 arası. */
    public static PortResult validatePort(String text) {
        if (text == null) {
            return PortResult.EMPTY;
        }
        String port = text.trim();
        if (port.isEmpty()) {
            return PortResult.EMPTY;
        }
        for (int i = 0; i < port.length(); i++) {
            char c = port.charAt(i);
            if (c < '0' || c > '9') {
                return PortResult.INVALID;
            }
        }
        if (port.length() > 5) {
            return PortResult.OUT_OF_RANGE; // sayı taşmasını önler
        }
        int value = Integer.parseInt(port);
        if (value < 1 || value > 65535) {
            return PortResult.OUT_OF_RANGE;
        }
        return PortResult.OK;
    }

    /** Yalnızca validatePort() OK döndürdükten sonra çağır. */
    public static int parsePort(String text) {
        return Integer.parseInt(text.trim());
    }
}