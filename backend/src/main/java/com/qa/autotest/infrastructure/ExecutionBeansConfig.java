package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.ArtifactVerifier;
import com.qa.autotest.application.execution.ExecutionPrecheck;
import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.FailureClassifier;
import com.qa.autotest.application.execution.FailureRuleSet;
import com.qa.autotest.application.execution.FileSystemProbe;
import com.qa.autotest.application.execution.LeaseService;
import com.qa.autotest.application.execution.LeaseWaitRegistry;
import com.qa.autotest.application.execution.NioFileSystemProbe;
import com.qa.autotest.application.execution.OutputXmlProbe;
import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.application.execution.PabotVersionProbe;
import com.qa.autotest.application.execution.ProcessLauncher;
import com.qa.autotest.application.execution.ProcessTreeKiller;
import com.qa.autotest.application.execution.RetryPolicy;
import com.qa.autotest.application.execution.RunIdGenerator;
import com.qa.autotest.application.execution.Sleeper;
import com.qa.autotest.application.execution.SshClient;
import com.qa.autotest.application.execution.ThreadSleeper;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** 执行域默认 Bean 装配。 */
@Configuration
public class ExecutionBeansConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock executionClock() {
        return Clock.system(ZONE);
    }

    @Bean
    public Sleeper sleeper() {
        return new ThreadSleeper();
    }

    @Bean
    public LeaseService leaseService(ResourceLeaseRepository leases, Clock executionClock,
                                     Sleeper sleeper, ExecutionProperties props,
                                     com.qa.autotest.application.iam.AuditService audit) {
        return new LeaseService(leases, executionClock,
                Duration.ofSeconds(props.getLeaseTtlSeconds()),
                Duration.ofSeconds(props.getLeaseHeartbeatSeconds()), sleeper, audit);
    }

    @Bean
    public RunIdGenerator runIdGenerator() {
        return new RunIdGenerator();
    }

    /** 租约等待登记：让「设备被占」在界面显示成等待资源而不是运行中（FR-08）。 */
    @Bean
    public LeaseWaitRegistry leaseWaitRegistry(Clock executionClock) {
        return new LeaseWaitRegistry(executionClock);
    }

    @Bean
    public FileSystemProbe fileSystemProbe() {
        return new NioFileSystemProbe();
    }

    @Bean
    public OutputXmlProbe outputXmlProbe() {
        return new OutputXmlProbe();
    }

    @Bean
    public ArtifactVerifier artifactVerifier(OutputXmlProbe probe) {
        return new ArtifactVerifier(probe);
    }

    @Bean
    public ProcessLauncher processLauncher() {
        return new ProcessLauncherImpl();
    }

    @Bean
    public PabotVersionProbe pabotVersionProbe() {
        return new PabotVersionProbeImpl();
    }

    /** DUT 采集走系统 OpenSSH（不引入 JSch，口令只经 SSHPASS 环境变量）。 */
    @Bean
    public SshClient sshClient() {
        return new SystemSshClient();
    }

    @Bean
    public ExecutionPrecheck executionPrecheck(FileSystemProbe files, PabotVersionProbe versions) {
        return new ExecutionPrecheck(files, versions);
    }

    @Bean
    public PabotCommandBuilder pabotCommandBuilder() {
        return new PabotCommandBuilder();
    }

    @Bean
    public ProcessTreeKiller processTreeKiller() {
        return new ProcessTreeKiller();
    }

    /** 失败规则集：优先取 execution.failure-rules，缺省时用 spec §4.7 默认规则。 */
    @Bean
    public FailureRuleSet failureRuleSet(ExecutionProperties props) {
        return FailureRuleSet.from(props.getFailureRules());
    }

    @Bean
    public FailureClassifier failureClassifier(FailureRuleSet failureRuleSet) {
        return new FailureClassifier(failureRuleSet);
    }

    @Bean
    public RetryPolicy retryPolicy() {
        return new RetryPolicy();
    }
}
