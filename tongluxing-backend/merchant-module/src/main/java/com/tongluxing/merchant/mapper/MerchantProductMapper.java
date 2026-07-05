package com.tongluxing.merchant.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.merchant.dto.MerchantQueryDTO;

/**
 * 商家商品数据访问接口。
 */
@Mapper
public interface MerchantProductMapper {

    /**
     * 分页查询商家商品。
     */
    @Select("""
            <script>
            select id,
                   merchant_id merchantId,
                   product_name productName,
                   product_type productType,
                   original_price originalPrice,
                   group_price groupPrice,
                   ladder_price_json ladderPriceJson,
                   target_people targetPeople,
                   stock,
                   valid_hours validHours,
                   min_settlement_price minSettlementPrice,
                   image_keys_json imageKeysJson,
                   description,
                   product_status productStatus,
                   updated_at updatedAt
            from merchant_product
            where merchant_id = #{merchantId}
              and deleted = 0
            <if test="status != null and status != ''">
              and product_status = #{status}
            </if>
            order by updated_at desc
            limit #{offset}, #{size}
            </script>
            """)
    List<MerchantQueryDTO> findByMerchant(@Param("merchantId") Long merchantId,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /**
     * 统计商家商品数量。
     */
    @Select("""
            <script>
            select count(*)
            from merchant_product
            where merchant_id = #{merchantId}
              and deleted = 0
            <if test="status != null and status != ''">
              and product_status = #{status}
            </if>
            </script>
            """)
    long countByMerchant(@Param("merchantId") Long merchantId, @Param("status") String status);

    /**
     * 查询商家单个商品。
     */
    @Select("""
            select id,
                   merchant_id merchantId,
                   product_name productName,
                   product_type productType,
                   original_price originalPrice,
                   group_price groupPrice,
                   ladder_price_json ladderPriceJson,
                   target_people targetPeople,
                   stock,
                   valid_hours validHours,
                   min_settlement_price minSettlementPrice,
                   image_keys_json imageKeysJson,
                   description,
                   product_status productStatus,
                   updated_at updatedAt
            from merchant_product
            where id = #{productId}
              and merchant_id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findById(@Param("merchantId") Long merchantId, @Param("productId") Long productId);

    /**
     * 按商品 ID 查询上架商品快照，供订单和拼团模块读取。
     */
    @Select("""
            select p.id,
                   p.merchant_id merchantId,
                   p.product_name productName,
                   p.product_type productType,
                   p.original_price originalPrice,
                   p.group_price groupPrice,
                   p.ladder_price_json ladderPriceJson,
                   p.target_people targetPeople,
                   p.stock,
                   p.valid_hours validHours,
                   p.min_settlement_price minSettlementPrice,
                   p.image_keys_json imageKeysJson,
                   p.description,
                   p.product_status productStatus,
                   p.updated_at updatedAt
            from merchant_product p
            inner join merchant_profile m on m.id = p.merchant_id
            where p.id = #{productId}
              and p.deleted = 0
              and p.product_status = 'ON_SHELF'
              and m.deleted = 0
              and m.audit_status = 'APPROVED'
              and m.status = 'ACTIVE'
            limit 1
            """)
    MerchantQueryDTO findSnapshotByProductId(@Param("productId") Long productId);

    /**
     * 锁定商品库存，避免拼团活动重复消耗超出商家库存。
     */
    @Update("""
            update merchant_product
            set stock = stock - #{quantity},
                updated_at = #{now}
            where id = #{productId}
              and product_status = 'ON_SHELF'
              and stock >= #{quantity}
              and deleted = 0
            """)
    int decreaseStock(@Param("productId") Long productId,
                      @Param("quantity") Integer quantity,
                      @Param("now") LocalDateTime now);

    /**
     * 新增拼团商品，默认直接上架。
     */
    @Insert("""
            insert into merchant_product(
                id, merchant_id, product_name, product_type, original_price,
                group_price, ladder_price_json, target_people, stock, valid_hours,
                min_settlement_price, image_keys_json, description, product_status,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{productName}, #{productType}, #{originalPrice},
                #{groupPrice}, #{ladderPriceJson}, #{targetPeople}, #{stock}, #{validHours},
                #{minSettlementPrice}, #{imageKeysJson}, #{description}, 'ON_SHELF',
                #{now}, #{now}, 0
            )
            """)
    int insertProduct(@Param("id") Long id,
                      @Param("merchantId") Long merchantId,
                      @Param("productName") String productName,
                      @Param("productType") String productType,
                      @Param("originalPrice") BigDecimal originalPrice,
                      @Param("groupPrice") BigDecimal groupPrice,
                      @Param("ladderPriceJson") String ladderPriceJson,
                      @Param("targetPeople") Integer targetPeople,
                      @Param("stock") Integer stock,
                      @Param("validHours") Integer validHours,
                      @Param("minSettlementPrice") BigDecimal minSettlementPrice,
                      @Param("imageKeysJson") String imageKeysJson,
                      @Param("description") String description,
                      @Param("now") LocalDateTime now);

    /**
     * 更新拼团商品资料。
     */
    @Update("""
            update merchant_product
            set product_name = #{productName},
                product_type = #{productType},
                original_price = #{originalPrice},
                group_price = #{groupPrice},
                ladder_price_json = #{ladderPriceJson},
                target_people = #{targetPeople},
                stock = #{stock},
                valid_hours = #{validHours},
                min_settlement_price = #{minSettlementPrice},
                image_keys_json = #{imageKeysJson},
                description = #{description},
                updated_at = #{now}
            where id = #{productId}
              and merchant_id = #{merchantId}
              and deleted = 0
            """)
    int updateProduct(@Param("merchantId") Long merchantId,
                      @Param("productId") Long productId,
                      @Param("productName") String productName,
                      @Param("productType") String productType,
                      @Param("originalPrice") BigDecimal originalPrice,
                      @Param("groupPrice") BigDecimal groupPrice,
                      @Param("ladderPriceJson") String ladderPriceJson,
                      @Param("targetPeople") Integer targetPeople,
                      @Param("stock") Integer stock,
                      @Param("validHours") Integer validHours,
                      @Param("minSettlementPrice") BigDecimal minSettlementPrice,
                      @Param("imageKeysJson") String imageKeysJson,
                      @Param("description") String description,
                      @Param("now") LocalDateTime now);

    /**
     * 下架商品。
     */
    @Update("""
            update merchant_product
            set product_status = 'OFF_SHELF',
                updated_at = #{now}
            where id = #{productId}
              and merchant_id = #{merchantId}
              and deleted = 0
            """)
    int offShelf(@Param("merchantId") Long merchantId,
                 @Param("productId") Long productId,
                 @Param("now") LocalDateTime now);
}
