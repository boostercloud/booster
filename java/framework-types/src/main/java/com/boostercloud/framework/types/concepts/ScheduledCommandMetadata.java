package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.ScheduleInterface;

/**
 * What {@code @ScheduledCommand} registers in {@code config.scheduledCommandHandlers[className]}.
 *
 * @param type the scheduled command class
 * @param scheduledOn when it runs
 */
public record ScheduledCommandMetadata(Class<?> type, ScheduleInterface scheduledOn) {}
