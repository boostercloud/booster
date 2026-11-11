package com.boostercloud.cli.services.generator.target;

/**
 * Everything needed to generate a resource file ({@code Target}).
 *
 * @param name the resource name, which is also the file name
 * @param extension the file extension, dot included ({@code .java})
 * @param placementDir the directory, relative to the project directory
 * @param template the Mustache template
 * @param info the view rendered with the template
 * @param <T> the type of the view
 */
public record Target<T>(String name, String extension, String placementDir, String template, T info)
    implements FileDir {}
