/* Copyright 2016 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.grpcmetadatagen;

import com.google.api.tools.framework.tools.ToolOptions;
import java.io.IOException;

/** Interface for the package copying phase of package metadata generation. */
public interface GrpcPackageCopier {

  /** Returns a map of Docs to be output, as well any package metadata generated in this phase. */
  public GrpcPackageCopierResult run(ToolOptions options) throws IOException;
}