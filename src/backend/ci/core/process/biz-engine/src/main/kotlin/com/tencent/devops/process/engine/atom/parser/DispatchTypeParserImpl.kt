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

package com.tencent.devops.process.engine.atom.parser

import com.tencent.devops.common.pipeline.matrix.DispatchInfo
import com.tencent.devops.common.pipeline.matrix.SampleDispatchInfo
import com.tencent.devops.common.pipeline.type.DispatchType
import com.tencent.devops.common.pipeline.type.StoreDispatchType
import com.tencent.devops.common.pipeline.type.docker.ImageType
import com.tencent.devops.process.engine.service.store.StoreImageHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component(value = "commonDispatchTypeParser")
class DispatchTypeParserImpl @Autowired constructor(
    private val storeImageHelper: StoreImageHelper
) : DispatchTypeParser {

    override fun parse(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        dispatchType: DispatchType
    ) {
        if (dispatchType !is StoreDispatchType) {
            return
        }
        // 凭证项目默认初始值为当前项目
        dispatchType.credentialProject = projectId
        if (dispatchType.imageType == ImageType.BKSTORE) {
            // 从商店获取镜像真实信息
            val imageRepoInfo = storeImageHelper.getImageRepoInfo(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                imageCode = dispatchType.imageCode,
                imageVersion = dispatchType.imageVersion,
                defaultPrefix = ""
            )

            val completeImageName = if (ImageType.BKDEVOPS == imageRepoInfo.sourceType) {
                // 蓝盾项目源镜像
                imageRepoInfo.repoName
            } else {
                // 第三方源镜像
                // dockerhub镜像名称不带斜杠前缀
                if (imageRepoInfo.repoUrl.isBlank()) {
                    imageRepoInfo.repoName
                } else {
                    "${imageRepoInfo.repoUrl}/${imageRepoInfo.repoName}"
                }
            } + ":" + imageRepoInfo.repoTag
            // 镜像来源替换为原始来源
            dispatchType.imageType = imageRepoInfo.sourceType
            dispatchType.value = completeImageName
            dispatchType.dockerBuildVersion = completeImageName
            dispatchType.credentialId = imageRepoInfo.ticketId
            dispatchType.credentialProject = imageRepoInfo.ticketProject
            dispatchType.imagePublicFlag = imageRepoInfo.publicFlag
            dispatchType.imageRDType = imageRepoInfo.rdType.name
        } else {
            dispatchType.credentialProject = projectId
        }
    }

    override fun parseInfo(customInfo: DispatchInfo, context: Map<String, String>): SampleDispatchInfo? {
        return null
    }
}
