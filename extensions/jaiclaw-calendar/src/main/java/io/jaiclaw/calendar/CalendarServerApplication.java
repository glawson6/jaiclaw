package io.jaiclaw.calendar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP server for the calendar extension.
 * Activated via the {@code -Pstandalone} Maven profile.
 */
@SpringBootApplication
public class CalendarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarServerApplication.class, args);
    }
}
