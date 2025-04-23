package org.example.eshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ImageOrderUpdateRequest {
    private Long productId;
    private Map<String, Integer> orderMap;
}
