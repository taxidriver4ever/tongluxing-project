package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.groupbuy.integration.GroupbuyMerchantProductPort;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.vo.MerchantProductVO;

import lombok.RequiredArgsConstructor;

/**
 * 拼团模块读取商家商品的应用层适配器。
 *
 * <p>实现 groupbuy-module 定义的商品端口，将商品快照读取和库存扣减委托给 merchant-module。</p>
 */
@Component
@RequiredArgsConstructor
public class GroupbuyMerchantProductAdapter implements GroupbuyMerchantProductPort {

    private final MerchantService merchantService;

    /**
     * 获取拼团创建所需的商品快照。
     *
     * @param productId 商品 ID
     * @return 拼团模块使用的商品快照 DTO
     */
    @Override
    public MerchantProductSnapshot getSnapshot(Long productId) {
        MerchantProductVO product = merchantService.productSnapshot(productId);
        return new MerchantProductSnapshot(product.productId(), product.merchantId(), product.productName(),
                product.productType(), product.originalPrice(), product.groupPrice(), product.ladderPriceJson(),
                product.targetPeople(), product.stock(), product.validHours());
    }

    /**
     * 扣减商品库存。
     *
     * @param productId 商品 ID
     * @param quantity 扣减数量
     * @param requestId 幂等请求号
     */
    @Override
    public void decreaseStock(Long productId, Integer quantity, String requestId) {
        merchantService.decreaseProductStock(productId, quantity, requestId);
    }
}
