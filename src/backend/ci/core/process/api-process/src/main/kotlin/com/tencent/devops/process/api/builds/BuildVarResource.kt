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

package com.tencent.devops.process.api.builds

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_BUILD_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PIPELINE_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.pojo.Result
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.Consumes
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["BUILD_VARIABLE"], description = "构建-构建参数")
@Path("/build/variable")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface BuildVarResource {
    @ApiOperation("获取指定构建或指定流水线下的构建变量")
    @Path("/getBuildVariable")
    @GET
    fun getBuildVar(
        @ApiParam(value = "构建ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_BUILD_ID)
        buildId: String,
        @ApiParam(value = "项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam(value = "流水线ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PIPELINE_ID)
        pipelineId: String
    ): Result<Map<String, String>>

    @ApiOperation("获取指定构建或指定构建下的上下文变量")
    @Path("/get_build_context")
    @GET
    fun getContextVariableByName(
        @ApiParam(value = "构建ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_BUILD_ID)
        buildId: String,
        @ApiParam(value = "项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam(value = "流水线ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PIPELINE_ID)
        pipelineId: String,
        @ApiParam(value = "变量名称", required = true)
        @QueryParam("contextName")
        contextName: String,
        @ApiParam(value = "是否校验变量", required = false)
        @QueryParam("check")
        check: Boolean = false
    ): Result<String?>
}
