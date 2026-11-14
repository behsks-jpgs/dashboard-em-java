package br.umc.thindesk.web.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Datas {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Datas() { }

    public static String iso(LocalDateTime dt) {
        return dt == null ? null : ISO.format(dt);
    }
}
