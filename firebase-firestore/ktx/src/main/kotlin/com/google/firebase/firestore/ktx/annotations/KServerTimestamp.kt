// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.ktx.annotations

import kotlinx.serialization.SerialInfo

/**
 * Annotation used to mark a timestamp field to be populated with a server timestamp. If a
 * @Serializable object being written contains {@code null} for a @ServerTimestamp-annotated field,
 * it will be replaced with a server-generated timestamp.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class KServerTimestamp()