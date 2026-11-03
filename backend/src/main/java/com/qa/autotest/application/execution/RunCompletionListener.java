package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.RunRecord;

/**
 * 运行终态完成监听（PLN-03 钩子）。
 *
 * <p>接口放在 application 层，domain 不依赖 Spring；实现可以是 CI 回写、通知等后续扩展。
 *
 * <p><b>契约</b>：
 * <ul>
 *   <li>只在状态机进入终态（FINISHED / FAILED / CANCELED）后调用，且有事务时在<b>提交后</b>执行；</li>
 *   <li>监听器抛出的异常由 {@link RunService} 吞掉并记录，<b>绝不改变 run 结论</b>，
 *       也不回滚已提交的状态；</li>
 *   <li>监听器在独立调用中自行处理租户上下文，不依赖请求线程。</li>
 * </ul>
 */
public interface RunCompletionListener {

    void onCompleted(RunRecord run);
}
