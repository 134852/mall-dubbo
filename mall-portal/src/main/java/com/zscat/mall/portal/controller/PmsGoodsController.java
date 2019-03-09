package com.zscat.mall.portal.controller;


import com.zscat.cms.service.CmsSubjectService;
import com.zscat.common.annotation.IgnoreAuth;
import com.zscat.common.result.CommonResult;
import com.zscat.mall.portal.constant.RedisKey;
import com.zscat.mall.portal.entity.MemberProductCollection;
import com.zscat.mall.portal.repository.MemberProductCollectionRepository;
import com.zscat.mall.portal.service.HomeService;
import com.zscat.mall.portal.service.MemberCollectionService;
import com.zscat.mall.portal.service.RedisService;
import com.zscat.mall.portal.util.JsonUtil;
import com.zscat.mall.portal.vo.R;
import com.zscat.pms.dto.ConsultTypeCount;
import com.zscat.pms.dto.PmsProductCategoryWithChildrenItem;
import com.zscat.pms.dto.PmsProductQueryParam;
import com.zscat.pms.dto.PmsProductResult;
import com.zscat.pms.model.PmsProductAttribute;
import com.zscat.pms.model.PmsProductAttributeCategory;
import com.zscat.pms.model.PmsProductConsult;
import com.zscat.pms.service.*;
import com.zscat.ums.model.UmsMember;
import com.zscat.ums.service.SmsHomeAdvertiseService;
import com.zscat.ums.service.UmsMemberService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页内容管理Controller
 * Created by zscat on 2019/1/28.
 */
@RestController
@Api(tags = "GoodsController", description = "首页内容管理")
@RequestMapping("/api/pms")
public class PmsGoodsController extends ApiBaseAction{
    @Resource
    private HomeService homeService;
    @Resource
    private PmsProductAttributeCategoryService productAttributeCategoryService;
    @Resource
    private SmsHomeAdvertiseService advertiseService;
    @Resource
    private PmsProductService pmsProductService;
    @Resource
    private PmsProductAttributeService productAttributeService;

    @Resource
    private PmsProductCategoryService productCategoryService;
    @Resource
    private CmsSubjectService subjectService;
    @Resource
    private UmsMemberService memberService;
    @Resource
    private PmsProductConsultService pmsProductConsultService;
    @Resource
    private RedisService redisService;
    @Resource
    private MemberCollectionService memberCollectionService;
    @Resource
    private MemberProductCollectionRepository productCollectionRepository;

    @IgnoreAuth
    @PostMapping(value = "/product/queryProductList")
    @ApiOperation(value = "查询商品列表")
    public R queryProductList(@RequestBody PmsProductQueryParam productQueryParam) {
        R r = new R();
        r.put("data", pmsProductService.list(productQueryParam));
        return r;
    }
    @IgnoreAuth
    @GetMapping(value = "/product/queryProductList1")
    public R queryProductList1(PmsProductQueryParam productQueryParam) {
        R r = new R();
        r.put("data", pmsProductService.list(productQueryParam));
        return r;
    }
    /**
     * 或者分类和分类下的商品
     *
     * @return
     */
    @IgnoreAuth
    @GetMapping("/getProductCategoryDto")
    public R getProductCategoryDtoByPid() {
        R r = new R();
        List<PmsProductAttributeCategory> productAttributeCategoryList = productAttributeCategoryService.getList(6, 1);
        for (PmsProductAttributeCategory gt : productAttributeCategoryList) {
            PmsProductQueryParam productQueryParam = new PmsProductQueryParam();
            productQueryParam.setProductAttributeCategoryId(gt.getId());
            productQueryParam.setPageNum(1);productQueryParam.setPageSize(4);
            gt.setGoodsList(pmsProductService.list(productQueryParam));
        }
        r.put("data", productAttributeCategoryList);
        return r;
    }

    /**
     * 查询所有一级分类及子分类
     *
     * @return
     */
    @IgnoreAuth
    @GetMapping("/listWithChildren")
    public Object listWithChildren() {
        List<PmsProductCategoryWithChildrenItem> list = productCategoryService.listWithChildren();
        return new CommonResult().success(list);
    }


    @IgnoreAuth
    @GetMapping(value = "/product/queryProductDetail")
    @ApiOperation( value = "查询商品详情信息")
    public Object queryProductDetail(@RequestParam(value = "id", required = false, defaultValue = "0") Long id) {
        PmsProductResult productResult = pmsProductService.getUpdateInfo(id);
        UmsMember umsMember = this.getCurrentMember();
        if (umsMember != null && umsMember.getId() != null) {
            MemberProductCollection findCollection = productCollectionRepository.findByMemberIdAndProductId(
                    umsMember.getId(), id);
            if(findCollection!=null){
                productResult.setIs_favorite(1);
            }else{
                productResult.setIs_favorite(2);
            }
        }
        return new CommonResult().success(productResult);
    }
    @IgnoreAuth
    @GetMapping(value = "/attr/list")
    public Object getList(@RequestParam(value = "cid", required = false, defaultValue = "0") Long cid,
                          @RequestParam(value = "type") Integer type,
                          @RequestParam(value = "pageSize", required = false,defaultValue = "5") Integer pageSize,
                          @RequestParam(value = "pageNum", required = false,defaultValue = "1") Integer pageNum) {
        List<PmsProductAttribute> productAttributeList = productAttributeService.getList(cid, type, pageSize, pageNum);
        return this.pageSuccess(productAttributeList);
    }

    @IgnoreAuth
    @ApiOperation("获取某个商品的评价")
    @RequestMapping(value = "/consult/list", method = RequestMethod.GET)
    @ResponseBody
    public Object list(@RequestParam(value = "goodsId", required = false, defaultValue = "0") Long goodsId,
                       @RequestParam(value = "pageNum", required = false,defaultValue = "1") Integer pageNum,
                       @RequestParam(value = "pageSize", required = false,defaultValue = "5") Integer pageSize) {

            PmsProductConsult productConsult = new PmsProductConsult();
            productConsult.setGoodsId(goodsId);
        List<PmsProductConsult> list = null;
        String consultJson = redisService.get(RedisKey.PmsProductConsult+goodsId);
        if(consultJson!=null){
            list = JsonUtil.jsonToList(consultJson,PmsProductConsult.class);
        }else {
            list = pmsProductConsultService.list(productConsult, 1,100000);
            redisService.set(RedisKey.PmsProductConsult+goodsId,JsonUtil.objectToJson(list));
            redisService.expire(RedisKey.PmsProductConsult+goodsId,24*60*60);
        }

        int goods =0;
        int general= 0;
        int bad =0;
        ConsultTypeCount count = new ConsultTypeCount();
        for (PmsProductConsult consult : list){
            if (consult.getStoreId()!=null){
                if (consult.getStoreId()==1){
                    goods++;
                }
                if (consult.getStoreId()==2){
                    general++;
                }
                if (consult.getStoreId()==3){
                    bad++;
                }
            }
        }
           count.setAll(goods+general+bad);
            count.setBad(bad);count.setGeneral(general);count.setGoods(goods);
            List<PmsProductConsult> productConsults = pmsProductConsultService.list(productConsult, pageNum,pageSize);
           Map<String,Object> objectMap = new HashMap<>();
           objectMap.put("list",productConsults);
           objectMap.put("count",count);


            return new CommonResult().success(objectMap);
    }
}
