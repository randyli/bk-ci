/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.common.dispatch.sdk.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.exception.ClientException
import com.tencent.devops.common.api.pojo.ErrorType
import com.tencent.devops.common.api.util.ApiUtil
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.dispatch.sdk.BuildFailureException
import com.tencent.devops.common.dispatch.sdk.DispatchSdkErrorCode
import com.tencent.devops.common.dispatch.sdk.pojo.DispatchMessage
import com.tencent.devops.common.dispatch.sdk.pojo.RedisBuild
import com.tencent.devops.common.dispatch.sdk.pojo.SecretInfo
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.monitoring.api.service.DispatchReportResource
import com.tencent.devops.monitoring.pojo.DispatchStatus
import com.tencent.devops.process.api.service.ServiceBuildResource
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.pojo.mq.PipelineAgentShutdownEvent
import com.tencent.devops.process.pojo.mq.PipelineAgentStartupEvent
import org.slf4j.LoggerFactory
import java.util.Date

class DispatchService constructor(
    private val redisOperation: RedisOperation,
    private val objectMapper: ObjectMapper,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val gateway: String?,
    private val client: Client,
    private val buildLogPrinter: BuildLogPrinter
) {

    fun log(buildId: String, containerHashId: String?, vmSeqId: String, message: String, executeCount: Int?) {
        buildLogPrinter.addLine(
            buildId,
            message,
            VMUtils.genStartVMTaskId(vmSeqId),
            containerHashId,
            executeCount ?: 1
        )
    }

    fun logRed(buildId: String, containerHashId: String?, vmSeqId: String, message: String, executeCount: Int?) {
        buildLogPrinter.addRedLine(
            buildId,
            message,
            VMUtils.genStartVMTaskId(vmSeqId),
            containerHashId,
            executeCount ?: 1
        )
    }

    fun buildDispatchMessage(event: PipelineAgentStartupEvent): DispatchMessage {
        logger.info("[${event.buildId}] Start build with gateway - ($gateway)")
        val secretInfo = setRedisAuth(event)
        return DispatchMessage(
            id = secretInfo.hashId,
            secretKey = secretInfo.secretKey,
            gateway = gateway!!,
            projectId = event.projectId,
            pipelineId = event.pipelineId,
            buildId = event.buildId,
            dispatchMessage = event.dispatchType.value,
            userId = event.userId,
            vmSeqId = event.vmSeqId,
            channelCode = event.channelCode,
            vmNames = event.vmNames,
            atoms = event.atoms,
            zone = event.zone,
            containerHashId = event.containerHashId,
            executeCount = event.executeCount,
            containerId = event.containerId,
            containerType = event.containerType,
            stageId = event.stageId,
            dispatchType = event.dispatchType,
            customBuildEnv = event.customBuildEnv
        )
    }

    fun shutdown(event: PipelineAgentShutdownEvent) {
        val secretInfoKey = secretInfoRedisKey(event.buildId)

        // job结束
        finishBuild(event.vmSeqId!!, event.buildId, event.executeCount ?: 1)
        redisOperation.hdelete(secretInfoKey, secretInfoRedisMapKey(event.vmSeqId!!, event.executeCount ?: 1))

        val keysSet = redisOperation.hkeys(secretInfoKey)
        if (keysSet == null || keysSet.isEmpty()) {
            redisOperation.delete(secretInfoKey)
        }
    }

    fun checkRunning(event: PipelineAgentStartupEvent) {
        // 判断流水线是否还在运行，如果已经停止则不在运行
        // 只有detail的信息是在shutdown事件发出之前就写入的，所以这里去builddetail的信息。
        // 为了兼容gitci的权限，这里把渠道号都改成GIT,以便去掉用户权限验证
        val record = client.get(ServiceBuildResource::class).getBuildDetailStatusWithoutPermission(
            event.userId,
            event.projectId,
            event.pipelineId,
            event.buildId,
            ChannelCode.BS
        )
        if (record.isNotOk() || record.data == null) {
            logger.warn("The build event($event) fail to check if pipeline is running because of ${record.message}")
            throw BuildFailureException(
                errorType = ErrorType.SYSTEM,
                errorCode = DispatchSdkErrorCode.PIPELINE_STATUS_ERROR,
                formatErrorMessage = "无法获取流水线状态",
                errorMessage = "无法获取流水线状态"
            )
        }
        val status = BuildStatus.parse(record.data)
        if (!status.isRunning()) {
            logger.warn("The build event($event) is not running")
            throw BuildFailureException(
                errorType = ErrorType.USER,
                errorCode = DispatchSdkErrorCode.PIPELINE_NOT_RUNNING,
                formatErrorMessage = "流水线已经不再运行",
                errorMessage = "流水线已经不再运行"
            )
        }
    }

    fun onContainerFailure(event: PipelineAgentStartupEvent, e: BuildFailureException) {
        logger.warn("[${event.buildId}|${event.vmSeqId}] Container startup failure")
        try {
            client.get(ServiceBuildResource::class).setVMStatus(
                projectId = event.projectId,
                pipelineId = event.pipelineId,
                buildId = event.buildId,
                vmSeqId = event.vmSeqId,
                status = BuildStatus.FAILED,
                errorType = e.errorType,
                errorCode = e.errorCode,
                errorMsg = e.formatErrorMessage
            )
        } catch (ignore: ClientException) {
            logger.error("SystemErrorLogMonitor|onContainerFailure|${event.buildId}|error=${e.message},${e.errorCode}")
        }
    }

    fun redispatch(event: PipelineAgentStartupEvent) {
        logger.info("Re-dispatch the agent event - ($event)")
        pipelineEventDispatcher.dispatch(event)
    }

    fun sendDispatchMonitoring(
        projectId: String,
        pipelineId: String,
        buildId: String,
        vmSeqId: String,
        actionType: String,
        retryTime: Int,
        routeKeySuffix: String?,
        startTime: Long,
        stopTime: Long,
        errorCode: Int,
        errorType: ErrorType?,
        errorMessage: String?
    ) {
        try {
            client.get(DispatchReportResource::class).dispatch(
                DispatchStatus(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    buildId = buildId,
                    vmSeqId = vmSeqId,
                    actionType = actionType,
                    retryCount = retryTime.toLong(),
                    channelCode = ChannelCode.BS,
                    buildType = routeKeySuffix!!,
                    startTime = startTime,
                    stopTime = stopTime,
                    errorCode = errorCode.toString(),
                    errorMsg = errorMessage,
                    errorType = errorType?.name ?: ""
                )
            )
        } catch (e: Exception) {
            logger.warn("[$pipelineId]|[$buildId]|[$vmSeqId]| sendDispatchMonitoring failed.", e.message)
        }
    }

    private fun finishBuild(vmSeqId: String, buildId: String, executeCount: Int) {
        val result = redisOperation.hget(secretInfoRedisKey(buildId), secretInfoRedisMapKey(vmSeqId, executeCount))
        if (result != null) {
            val secretInfo = JsonUtil.to(result, SecretInfo::class.java)
            redisOperation.delete(redisKey(secretInfo.hashId, secretInfo.secretKey))
            logger.warn("$buildId|$vmSeqId finishBuild success.")
        } else {
            logger.error("$buildId|$vmSeqId finishBuild failed, secretInfo is null.")
        }
    }

    private fun setRedisAuth(event: PipelineAgentStartupEvent): SecretInfo {
        val secretInfoRedisKey = secretInfoRedisKey(event.buildId)
        val redisResult = redisOperation.hget(key = secretInfoRedisKey,
            hashKey = secretInfoRedisMapKey(event.vmSeqId, event.executeCount ?: 1)
        )
        if (redisResult != null) {
            return JsonUtil.to(redisResult, SecretInfo::class.java)
        }
        val secretKey = ApiUtil.randomSecretKey()
        val hashId = HashUtil.encodeLongId(System.currentTimeMillis())
        logger.info("[${event.buildId}|${event.vmSeqId}] Start to build the event with ($hashId|$secretKey)")
        redisOperation.set(
            key = redisKey(hashId, secretKey),
            value = objectMapper.writeValueAsString(
                RedisBuild(
                    vmName = if (event.vmNames.isBlank()) "Dispatcher-sdk-${event.vmSeqId}" else event.vmNames,
                    projectId = event.projectId,
                    pipelineId = event.pipelineId,
                    buildId = event.buildId,
                    vmSeqId = event.vmSeqId,
                    channelCode = event.channelCode,
                    zone = event.zone,
                    atoms = event.atoms,
                    executeCount = event.executeCount ?: 1
                )
            ),
            expiredInSecond = 7 * 24 * 3600
        )

        // 一周过期时间
        redisOperation.hset(
            secretInfoRedisKey(event.buildId),
            secretInfoRedisMapKey(event.vmSeqId, event.executeCount ?: 1),
            JsonUtil.toJson(SecretInfo(hashId, secretKey))
        )
        val expireAt = System.currentTimeMillis() + 24 * 7 * 3600 * 1000
        redisOperation.expireAt(secretInfoRedisKey, Date(expireAt))
        return SecretInfo(
            hashId = hashId,
            secretKey = secretKey
        )
    }

    private fun redisKey(hashId: String, secretKey: String) =
        "docker_build_key_${hashId}_$secretKey"

    private fun secretInfoRedisKey(buildId: String) =
        "secret_info_key_$buildId"

    private fun secretInfoRedisMapKey(vmSeqId: String, executeCount: Int) = "$vmSeqId-$executeCount"

    companion object {
        private val logger = LoggerFactory.getLogger(DispatchService::class.java)
    }
}
