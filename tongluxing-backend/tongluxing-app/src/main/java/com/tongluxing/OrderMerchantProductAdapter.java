package com.tongluxing;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.vo.MerchantProductVO;
import com.tongluxing.order.integration.OrderMerchantProductPort;

import lombok.RequiredArgsConstructor;

/**
 * 订单模块读取商家商品的应用层适配器。
 *
 * <p>实现 order-module 定义的商品端口，为订单试算和下单提供可固化的商品快照。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderMerchantProductAdapter implements OrderMerchantProductPort {

    private final MerchantService merchantService;
    private final ObjectMapper objectMapper;

    /**
     * 获取订单模块需要的商品快照。
     *
     * @param productId 商品 ID
     * @return 订单模块商品快照 DTO
     */
    @Override
    public MerchantProductSnapshot getSnapshot(Long productId) {
        MerchantProductVO product = merchantService.productSnapshot(productId);
        return new MerchantProductSnapshot(product.productId(), product.merchantId(), product.productName(),
                product.productType(), product.originalPrice(), product.groupPrice(), writeSnapshot(product));
    }

    /**
     * 将商家商品响应序列化为订单明细快照 JSON。
     */
    private String writeSnapshot(MerchantProductVO product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "商品快照序列化失败");
        }
    }
}

