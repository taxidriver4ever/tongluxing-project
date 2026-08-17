package com.tongluxing.infrastructure.location;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Produces the coarse IP region shown on a public profile.
 *
 * <p>The raw login IP never leaves the backend. Deployments may persist the
 * province supplied by a trusted gateway in the existing login-region field;
 * local/private addresses fall back to the user's declared city so local
 * development still has a useful province-level value.</p>
 */
@Component
public class IpProvinceResolver {
    private static final String[] PROVINCES = {
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北",
            "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州", "云南", "西藏",
            "陕西", "甘肃", "青海", "宁夏", "新疆", "香港", "澳门", "台湾"
    };

    private static final Map<String, String> CITY_PROVINCES = cityProvinces();

    public String resolve(String loginIpOrProvince, String cityName) {
        String direct = provinceToken(loginIpOrProvince);
        if (direct != null) {
            return direct;
        }
        if (cityName != null && !cityName.isBlank()) {
            String cityProvince = provinceToken(cityName);
            if (cityProvince != null) {
                return cityProvince;
            }
            for (Map.Entry<String, String> entry : CITY_PROVINCES.entrySet()) {
                if (cityName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return "未知";
    }

    private String provinceToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String province : PROVINCES) {
            if (value.contains(province)) {
                return province;
            }
        }
        return null;
    }

    private static Map<String, String> cityProvinces() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("杭州", "浙江"); values.put("宁波", "浙江"); values.put("温州", "浙江");
        values.put("广州", "广东"); values.put("深圳", "广东"); values.put("佛山", "广东");
        values.put("东莞", "广东"); values.put("珠海", "广东"); values.put("汕头", "广东");
        values.put("揭阳", "广东"); values.put("潮州", "广东"); values.put("惠州", "广东");
        values.put("南京", "江苏"); values.put("苏州", "江苏"); values.put("无锡", "江苏");
        values.put("成都", "四川"); values.put("武汉", "湖北"); values.put("长沙", "湖南");
        values.put("西安", "陕西"); values.put("郑州", "河南"); values.put("济南", "山东");
        values.put("青岛", "山东"); values.put("福州", "福建"); values.put("厦门", "福建");
        values.put("南昌", "江西"); values.put("合肥", "安徽"); values.put("昆明", "云南");
        values.put("贵阳", "贵州"); values.put("海口", "海南"); values.put("三亚", "海南");
        values.put("沈阳", "辽宁"); values.put("大连", "辽宁"); values.put("长春", "吉林");
        values.put("哈尔滨", "黑龙江"); values.put("石家庄", "河北"); values.put("太原", "山西");
        values.put("兰州", "甘肃"); values.put("西宁", "青海"); values.put("银川", "宁夏");
        values.put("乌鲁木齐", "新疆"); values.put("拉萨", "西藏"); values.put("南宁", "广西");
        values.put("呼和浩特", "内蒙古");
        return values;
    }
}
