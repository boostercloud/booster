package com.example.cartdemo.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.types.concepts.UUID;

@Entity
public record Post(
    UUID id
) {
}
