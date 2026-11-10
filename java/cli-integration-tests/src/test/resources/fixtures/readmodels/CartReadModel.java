package com.example.cartdemo.readmodels;

import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.UUID;

@ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record CartReadModel(
    UUID id
) {
}
