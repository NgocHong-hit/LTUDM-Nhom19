package common;

import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9]+(?:[._%+-][A-Za-z0-9]+)*@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,63}$");

    private ValidationUtil() {
    }

    public static boolean isValidEmail(String email) {
        return email != null
                && email.length() <= 254
                && EMAIL_PATTERN.matcher(email).matches();
    }
}
