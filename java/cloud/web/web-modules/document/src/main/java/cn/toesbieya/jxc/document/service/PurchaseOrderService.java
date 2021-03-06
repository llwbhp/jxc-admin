package cn.toesbieya.jxc.document.service;

import cn.toesbieya.jxc.api.service.RecordApi;
import cn.toesbieya.jxc.api.vo.AttachmentOperation;
import cn.toesbieya.jxc.common.model.entity.BizDocHistory;
import cn.toesbieya.jxc.common.model.entity.BizPurchaseOrder;
import cn.toesbieya.jxc.common.model.entity.BizPurchaseOrderSub;
import cn.toesbieya.jxc.common.model.entity.RecAttachment;
import cn.toesbieya.jxc.common.model.vo.Result;
import cn.toesbieya.jxc.common.model.vo.UserVo;
import cn.toesbieya.jxc.document.enumeration.DocHistoryEnum;
import cn.toesbieya.jxc.document.enumeration.DocStatusEnum;
import cn.toesbieya.jxc.document.mapper.DocumentHistoryMapper;
import cn.toesbieya.jxc.document.mapper.PurchaseOrderMapper;
import cn.toesbieya.jxc.document.mapper.PurchaseOrderSubMapper;
import cn.toesbieya.jxc.document.model.vo.DocStatusUpdate;
import cn.toesbieya.jxc.document.model.vo.PurchaseOrderExport;
import cn.toesbieya.jxc.document.model.vo.PurchaseOrderSearch;
import cn.toesbieya.jxc.document.model.vo.PurchaseOrderVo;
import cn.toesbieya.jxc.document.util.DocUtil;
import cn.toesbieya.jxc.web.common.annoation.Lock;
import cn.toesbieya.jxc.web.common.annoation.UserAction;
import cn.toesbieya.jxc.web.common.model.vo.PageResult;
import cn.toesbieya.jxc.web.common.utils.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Service
public class PurchaseOrderService {
    @Resource
    private PurchaseOrderMapper mainMapper;
    @Resource
    private PurchaseOrderSubMapper subMapper;
    @Resource
    private DocumentHistoryMapper historyMapper;
    @Reference
    private RecordApi recordApi;

    //组装子表、附件列表的数据
    public PurchaseOrderVo getById(String id) {
        BizPurchaseOrder main = mainMapper.selectById(id);

        if (main == null) return null;

        PurchaseOrderVo vo = new PurchaseOrderVo(main);

        vo.setData(this.getSubById(id));
        vo.setImageList(recordApi.getAttachmentByPid(id));

        return vo;
    }

    //根据主表ID获取子表
    public List<BizPurchaseOrderSub> getSubById(String id) {
        return subMapper.selectList(
                Wrappers.lambdaQuery(BizPurchaseOrderSub.class)
                        .eq(BizPurchaseOrderSub::getPid, id)
        );
    }

    public PageResult<BizPurchaseOrder> search(PurchaseOrderSearch vo) {
        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(mainMapper.selectList(getSearchCondition(vo)));
    }

    public void export(PurchaseOrderSearch vo, HttpServletResponse response) throws Exception {
        List<PurchaseOrderExport> list = mainMapper.export(getSearchCondition(vo));
        ExcelUtil.exportSimply(list, response, "采购订单导出");
    }

    @UserAction("'添加采购订单'")
    @Transactional(rollbackFor = Exception.class)
    public Result add(PurchaseOrderVo doc) {
        return addMain(doc);
    }

    @UserAction("'修改采购订单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public Result update(PurchaseOrderVo doc) {
        return updateMain(doc);
    }

    @UserAction("'提交采购订单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public Result commit(PurchaseOrderVo doc) {
        boolean isFirstCreate = StringUtils.isEmpty(doc.getId());
        Result result = isFirstCreate ? addMain(doc) : updateMain(doc);

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(doc.getId())
                        .type(DocHistoryEnum.COMMIT.getCode())
                        .uid(doc.getCid())
                        .uname(doc.getCname())
                        .status_before(DocStatusEnum.DRAFT.getCode())
                        .status_after(DocStatusEnum.WAIT_VERIFY.getCode())
                        .time(System.currentTimeMillis())
                        .build()
        );

        result.setMsg(result.isSuccess() ? "提交成功" : "提交失败，" + result.getMsg());

        return result;
    }

    @UserAction("'撤回采购订单'+#vo.id")
    @Lock("#vo.id")
    @Transactional(rollbackFor = Exception.class)
    public Result withdraw(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();

        if (this.rejectById(id) < 1) {
            return Result.fail("撤回失败，请刷新重试");
        }

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.WITHDRAW.getCode())
                        .uid(user.getId())
                        .uname(user.getName())
                        .status_before(DocStatusEnum.WAIT_VERIFY.getCode())
                        .status_after(DocStatusEnum.DRAFT.getCode())
                        .time(System.currentTimeMillis())
                        .info(info)
                        .build()
        );

        return Result.success("撤回成功");
    }

    @UserAction("'通过采购订单'+#vo.id")
    @Lock("#vo.id")
    @Transactional(rollbackFor = Exception.class)
    public Result pass(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();
        long now = System.currentTimeMillis();

        if (0 == mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseOrder.class)
                        .set(BizPurchaseOrder::getStatus, DocStatusEnum.VERIFIED.getCode())
                        .set(BizPurchaseOrder::getVid, user.getId())
                        .set(BizPurchaseOrder::getVname, user.getName())
                        .set(BizPurchaseOrder::getVtime, now)
                        .eq(BizPurchaseOrder::getId, id)
                        .eq(BizPurchaseOrder::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        )) {
            return Result.fail("通过失败，请刷新重试");
        }

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.PASS.getCode())
                        .uid(user.getId())
                        .uname(user.getName())
                        .status_before(DocStatusEnum.WAIT_VERIFY.getCode())
                        .status_after(DocStatusEnum.VERIFIED.getCode())
                        .time(now)
                        .info(info)
                        .build()
        );

        return Result.success("通过成功");
    }

    @UserAction("'驳回采购订单'+#vo.id")
    @Lock("#vo.id")
    @Transactional(rollbackFor = Exception.class)
    public Result reject(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();

        if (this.rejectById(id) < 1) {
            return Result.fail("驳回失败，请刷新重试");
        }

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.REJECT.getCode())
                        .uid(user.getId())
                        .uname(user.getName())
                        .status_before(DocStatusEnum.WAIT_VERIFY.getCode())
                        .status_after(DocStatusEnum.DRAFT.getCode())
                        .time(System.currentTimeMillis())
                        .info(info)
                        .build()
        );

        return Result.success("驳回成功");
    }

    @UserAction("'删除采购订单'+#id")
    @Lock("#id")
    @Transactional(rollbackFor = Exception.class)
    public Result del(String id) {
        if (mainMapper.deleteById(id) < 1) {
            return Result.fail("删除失败");
        }

        //同时删除子表和附件
        this.delSubByPid(id);
        recordApi.delAttachmentByPid(id);

        return Result.success("删除成功");
    }

    private Result addMain(PurchaseOrderVo doc) {
        String id = DocUtil.getDocumentID("CGDD");

        if (StringUtils.isEmpty(id)) {
            return Result.fail("获取单号失败");
        }

        doc.setId(id);

        List<BizPurchaseOrderSub> subList = doc.getData();

        //设置子表的pid、剩余未出库数量
        for (BizPurchaseOrderSub sub : subList) {
            sub.setPid(id);
            sub.setRemain_num(sub.getNum());
        }

        //插入主表和子表
        mainMapper.insert(doc);
        subMapper.insertBatch(subList);

        //插入附件
        List<RecAttachment> uploadImageList = doc.getUploadImageList();
        Long time = System.currentTimeMillis();
        for (RecAttachment attachment : uploadImageList) {
            attachment.setPid(id);
            attachment.setTime(time);
        }
        recordApi.handleAttachment(new AttachmentOperation(uploadImageList, null));

        return Result.success("添加成功", id);
    }

    private Result updateMain(PurchaseOrderVo doc) {
        String docId = doc.getId();

        String err = checkUpdateStatus(docId);
        if (err != null) return Result.fail(err);

        //更新主表
        mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseOrder.class)
                        .set(BizPurchaseOrder::getSid, doc.getSid())
                        .set(BizPurchaseOrder::getSname, doc.getSname())
                        .set(BizPurchaseOrder::getStatus, doc.getStatus())
                        .set(BizPurchaseOrder::getTotal, doc.getTotal())
                        .set(BizPurchaseOrder::getRemark, doc.getRemark())
                        .eq(BizPurchaseOrder::getId, docId)
        );

        //删除旧的子表
        this.delSubByPid(docId);

        //插入新的子表
        List<BizPurchaseOrderSub> subList = doc.getData();
        subList.forEach(sub -> sub.setRemain_num(sub.getNum()));
        subMapper.insertBatch(subList);

        //附件增删
        List<RecAttachment> uploadImageList = doc.getUploadImageList();
        Long time = System.currentTimeMillis();
        for (RecAttachment attachment : uploadImageList) {
            attachment.setPid(docId);
            attachment.setTime(time);
        }
        recordApi.handleAttachment(new AttachmentOperation(uploadImageList, doc.getDeleteImageList()));

        return Result.success("修改成功");
    }

    //只有拟定状态的单据才能修改
    private String checkUpdateStatus(String id) {
        BizPurchaseOrder doc = mainMapper.selectById(id);
        if (doc == null || !doc.getStatus().equals(DocStatusEnum.DRAFT.getCode())) {
            return "单据状态已更新，请刷新后重试";
        }
        return null;
    }

    //根据主表ID删除子表
    private void delSubByPid(String pid) {
        subMapper.delete(
                Wrappers.lambdaQuery(BizPurchaseOrderSub.class)
                        .eq(BizPurchaseOrderSub::getPid, pid)
        );
    }

    //驳回单据，只有等待审核单据的才能被驳回
    private int rejectById(String id) {
        return mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseOrder.class)
                        .set(BizPurchaseOrder::getStatus, DocStatusEnum.DRAFT.getCode())
                        .eq(BizPurchaseOrder::getId, id)
                        .eq(BizPurchaseOrder::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        );
    }

    private Wrapper<BizPurchaseOrder> getSearchCondition(PurchaseOrderSearch vo) {
        Integer sid = vo.getSid();
        String sname = vo.getSname();
        String finish = vo.getFinish();
        Long ftimeStart = vo.getFtimeStart();
        Long ftimeEnd = vo.getFtimeEnd();

        return DocUtil.baseCondition(BizPurchaseOrder.class, vo)
                .eq(sid != null, BizPurchaseOrder::getSid, sid)
                .like(!StringUtils.isEmpty(sname), BizPurchaseOrder::getSname, sname)
                .inSql(!StringUtils.isEmpty(finish), BizPurchaseOrder::getFinish, finish)
                .ge(ftimeStart != null, BizPurchaseOrder::getCtime, ftimeStart)
                .le(ftimeEnd != null, BizPurchaseOrder::getCtime, ftimeEnd);
    }
}
