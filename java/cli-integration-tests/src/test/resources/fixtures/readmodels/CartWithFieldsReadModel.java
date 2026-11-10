package com.example.cartdemo.readmodels;

import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

@ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record CartWithFieldsReadModel(
    UUID id,
    List<Item> items
) {
}
