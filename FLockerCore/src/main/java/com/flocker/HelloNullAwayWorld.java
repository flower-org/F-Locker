package com.flocker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class HelloNullAwayWorld {
    final static Logger LOGGER = LoggerFactory.getLogger(HelloNullAwayWorld.class);

    public static void main(String[] args) {
        String username = getUsername();
        printUsername(username);
        printUsername2(checkNotNull(username));
    }

    @Nullable static String getUsername() {
        return "Name";
    }

    static void printUsername(@Nullable String username) {
        if (username != null) {
            checkNotNull(username);
            username = "Hello, " + username + "!";
            LOGGER.info(username);
            System.out.println(username);
        }
    }

    static void printUsername2(String username) {
        username = "Hello, " + username + "!";
        LOGGER.info(username);
        System.out.println(username);
    }
}
