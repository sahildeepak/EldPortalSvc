package com.ms.eldportal.model.request;

import lombok.Data;

@Data
public class SearchKey {
    private String userId;
    private String searchValue;
    private String category;

}
