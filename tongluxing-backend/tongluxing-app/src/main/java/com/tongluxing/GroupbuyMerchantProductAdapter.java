package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.groupbuy.integration.GroupbuyMerchantProductPort;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.vo.MerchantProductVO;

import lombok.RequiredArgsConstructor;

/**
 * 拼团模块读取商家商品的应用层适配器。
 */
@Component
@RequiredArgsConstructor
public class GroupbuyMerchantProductAdapter implements GroupbuyMerchantProductPort {

    private final MerchantService merchantService;

    @Override
    public MerchantProductSnapshot getSnapshot(Long productId) {
        MerchantProductVO product = merchantService.productSnapshot(productId);
        return new MerchantProductSnapshot(product.productId(), product.merchantId(), product.productName(),
                product.productType(), product.originalPrice(), product.groupPrice(), product.ladderPriceJson(),
                product.targetPeople(), product.stock(), product.validHours());
    }

    @Override
    public void decreaseStock(Long productId, Integer quantity, String requestId) {
        merchantService.decreaseProductStock(productId, quantity, requestId);
    }
}
