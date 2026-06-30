package com.mk3.chatapp.utils;

import java.util.Locale;
import java.util.Set;

public final class RootAdminUsers {

    private static final Set<String> ROOT_ADMIN_EMAILS = Set.of("mattstanton94@yahoo.com");
    private static final Set<String> ROOT_ADMIN_USERNAMES = Set.of("markok3");

    private RootAdminUsers() {
    }

    public static Set<String> emails() {
        return ROOT_ADMIN_EMAILS;
    }

    public static Set<String> usernames() {
        return ROOT_ADMIN_USERNAMES;
    }

    public static boolean matches(String username, String email) {
        return ROOT_ADMIN_USERNAMES.contains(normalize(username))
                || ROOT_ADMIN_EMAILS.contains(normalize(email));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
