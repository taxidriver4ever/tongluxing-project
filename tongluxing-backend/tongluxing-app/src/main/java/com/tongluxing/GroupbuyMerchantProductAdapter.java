package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.groupbuy.integration.GroupbuyMerchantProductPort;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.MerchantCouponOfferVO;
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
    private final MerchantEcosystemService ecosystemService;

    /**
     * 获取拼团创建所需的商品快照。
     *
     * @param productId 商品 ID
     * @return 拼团模块使用的商品快照 DTO
     */
    @Override
    public MerchantProductSnapshot getSnapshot(Long productId) {
        try {
            MerchantCouponOfferVO offer = ecosystemService.offerForAdmin(productId);
            boolean available = offer.groupEnabled() && "APPROVED".equals(offer.auditStatus())
                    && "ACTIVE".equals(offer.offerStatus()) && offer.stock() != null && offer.stock() > 0;
            return new MerchantProductSnapshot(offer.couponId(), offer.merchantId(), offer.merchantName(),
                    offer.couponName(), "GROUP_COUPON", offer.originalPrice(), offer.salePrice(), "[]",
                    offer.groupPeople(), offer.stock(), offer.groupTimeoutHours(), offer.storeId(), offer.storeName(),
                    offer.storeAddress(), true, available);
        } catch (RuntimeException ignored) {
            MerchantProductVO product = merchantService.productSnapshot(productId);
            return new MerchantProductSnapshot(product.productId(), product.merchantId(), "合作商家", product.productName(),
                    product.productType(), product.originalPrice(), product.groupPrice(), product.ladderPriceJson(),
                    product.targetPeople(), product.stock(), product.validHours(), null, null, null, false,
                    "ON_SHELF".equals(product.productStatus()) && product.stock() != null && product.stock() > 0);
        }
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
        try {
            MerchantCouponOfferVO offer = ecosystemService.offerForAdmin(productId);
            if (offer.groupEnabled()) {
                ecosystemService.reserveGroupbuyStock(productId, quantity);
                return;
            }
        } catch (RuntimeException ignored) {
            // 非优惠券 ID 时继续兼容旧商家商品拼团。
        }
        merchantService.decreaseProductStock(productId, quantity, requestId);
    }
}
