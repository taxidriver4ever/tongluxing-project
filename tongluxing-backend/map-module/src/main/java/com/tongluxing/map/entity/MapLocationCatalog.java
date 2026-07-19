package com.tongluxing.map.entity;

import java.math.BigDecimal;

import lombok.Data;

/** 可搜索地点目录实体。 */
@Data
public class MapLocationCatalog {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String keywords;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer sortNo;
}
