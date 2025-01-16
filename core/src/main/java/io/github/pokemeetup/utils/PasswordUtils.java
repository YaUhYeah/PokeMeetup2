package io.github.pokemeetup.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordUtils {

    public static String hashPassword(String password) {
        try {
            return BCrypt.withDefaults().hashToString(10, password.toCharArray());
        } catch (Exception e) {
            GameLogger.info("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyPassword(String plainPassword, String storedHash) {
        try {
            if (storedHash == null) {
                GameLogger.info("Stored hash is null for password verification");
                return false;
            }
            return BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash).verified;
        } catch (Exception e) {
            GameLogger.info("Error verifying password: " + e.getMessage());
            return false;
        }
    }
}
