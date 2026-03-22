package com.example.crawler.shared;

/**
 * Office location for an attorney.
 *
 * @param state the state name
 * @param city the city name
 * @param abbreviation the state abbreviation
 */
public record OfficeLocation(
        String state,
        String city,
        String abbreviation
) {}
