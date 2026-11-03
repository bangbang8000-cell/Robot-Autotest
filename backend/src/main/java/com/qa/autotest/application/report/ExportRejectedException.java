package com.qa.autotest.application.report;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.List;

/**
 * 外部版导出被 metadata 门禁拒绝（RPT-05）。
 *
 * <p>除错误码与中文说明外，还携带 {@code metadataMissing} 数组，供前端直接列出缺项。
 */
public class ExportRejectedException extends BusinessException {

    private final List<String> metadataMissing;

    public ExportRejectedException(List<String> metadataMissing) {
        super(ErrorCode.INVALID_ARGUMENT,
                "外部版导出被拒绝：缺少 metadata 项 " + String.join(", ", metadataMissing));
        this.metadataMissing = List.copyOf(metadataMissing);
    }

    public List<String> metadataMissing() {
        return metadataMissing;
    }
}
