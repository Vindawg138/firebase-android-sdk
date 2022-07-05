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

package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AssertThrows
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreMapEncoderTests {

    @Test
    fun `plain custom object encoding is supported`() {
        @Serializable data class PlainProject(val name: String, val ownerName: String)
        val plainObject = PlainProject("kotlinx.serialization", "kotlin")
        val encodedMap = encodeToMap(plainObject)
        val expectedMap = mapOf("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Project(val name: String, val owner: Owner)
        val project = Project("kotlinx.serialization", Owner("kotlin"))
        val encodedMap = encodeToMap(project)
        val expectedMap =
            mapOf("name" to "kotlinx.serialization", "owner" to mapOf("name" to "kotlin"))
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested primitive list inside of custom object encoding is supported`() {
        @Serializable data class Product(val name: String, val serialNumList: List<Long>)
        val product = Product("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        val encodedMap = encodeToMap(product)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.serialization",
                "serialNumList" to listOf(1L, 10L, 100L, 1000L)
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested custom obj list inside of custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Store(val name: String, val listOfOwner: List<Owner>)
        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val store = Store("kotlinx.store", listOfOwner)
        val encodedMap = encodeToMap(store)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.store",
                "listOfOwner" to
                    listOf(mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Serializable
    enum class Direction {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    @Test
    fun `enum field encoding is supported`() {
        @Serializable data class Movement(val direction: Direction, val distance: Long)
        val movement = Movement(Direction.EAST, 100)
        val encodedMap = encodeToMap(movement)
        val expectedMap = mapOf("direction" to "EAST", "distance" to 100L)
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `null-able field encoding is supported`() {
        @Serializable data class Visitor(val name: String? = null, val age: String)
        val visitor = Visitor(age = "100")
        val encodedMap = encodeToMap(visitor)
        val expectedMap = mutableMapOf("name" to null, "age" to "100")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    // need to copy more test from Line 1428
    @Test
    fun encodeStringBean() {
        @Serializable data class StringBean(val value: String? = null)
        val bean = StringBean("foo")
        val expectedMap = mutableMapOf("value" to "foo")
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeDoubleBean() {
        @Serializable data class DoubleBean(val value: Double? = null)
        val bean = DoubleBean(1.1)
        val expectedMap = mutableMapOf("value" to 1.1)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeIntBean() {
        @Serializable data class IntBean(val value: Int? = null)
        val bean = IntBean(1)
        val expectedMap = mutableMapOf("value" to 1)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeLongBean() {
        @Serializable data class LongBean(val value: Long? = null)
        val bean = LongBean(Int.MAX_VALUE + 100L)
        val expectedMap = mutableMapOf("value" to Int.MAX_VALUE + 100L)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeBooleanBean() {
        @Serializable data class BooleanBean(val value: Boolean? = null)
        val bean = BooleanBean(true)
        val expectedMap = mutableMapOf("value" to true)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `unicode object encoding is supported`() {
        @Serializable data class UnicodeObject(val 漢字: String? = null)
        val unicodeObject = UnicodeObject(漢字 = "foo")
        val encodedMap = encodeToMap(unicodeObject)
        val expectedMap = mutableMapOf("漢字" to "foo")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `short encoding is supported`() {
        // encoding supports converting an object with short field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ShortObject(val value: Short? = null)
        val shortObject = ShortObject(value = 1)
        val encodedMap = encodeToMap(shortObject)
        val expectedMap = mutableMapOf("value" to 1.toShort())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `byte encoding is supported`() {
        // encoding supports converting an object with byte field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ByteObject(val value: Byte? = null)
        val byteObject = ByteObject(value = 1)
        val encodedMap = encodeToMap(byteObject)
        val expectedMap = mutableMapOf("value" to 1.toByte())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `chars encoding is supported`() {
        // encoding supports converting an object with char field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class CharObject(val value: Char? = null)
        val charObject = CharObject(value = 1.toChar())
        val encodedMap = encodeToMap(charObject)
        val expectedMap = mutableMapOf("value" to 1.toChar())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `array encoding is supported`() {
        // encoding supports both array and list;
        @Serializable
        data class IntArrayObject(
            val kotlinArrayValue: Array<Int>? = null,
            val listValue: List<Int>? = null,
            val javaArrayValue: IntArray? = null
        )

        val array = arrayOf(1, 2, 3)
        val list = listOf(4, 5, 6)
        val javaIntArray = intArrayOf(7, 8, 9)
        val intArrayObject =
            IntArrayObject(
                kotlinArrayValue = array,
                listValue = list,
                javaArrayValue = javaIntArray
            )
        val encodedMap = encodeToMap(intArrayObject)
        val expectedMap =
            mutableMapOf(
                "kotlinArrayValue" to listOf(1, 2, 3),
                "listValue" to listOf(4, 5, 6),
                "javaArrayValue" to listOf(7, 8, 9)
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Serializable private data class GenericObject<T>(val value: T? = null)

    @Serializable
    private data class DoubleGenericObject<A, B>(val valueA: A? = null, val valueB: B? = null)

    @Test
    fun `generic encoding is supported`() {
        val stringObj = GenericObject("foo")
        val encodedMapOfStringObj = encodeToMap(stringObj)
        val expectedMapOfStringObj = mutableMapOf("value" to "foo")
        assertThat(encodedMapOfStringObj).containsExactlyEntriesIn(expectedMapOfStringObj)

        val list = listOf("foo", "bar")
        val listObj = GenericObject(list)
        val encodedMapOfListObj = encodeToMap(listObj)
        val expectedMapOfListObj = mutableMapOf("value" to listOf("foo", "bar"))
        assertThat(encodedMapOfListObj).containsExactlyEntriesIn(expectedMapOfListObj)

        val innerObj = GenericObject("foo")
        val recursiveObj = GenericObject(innerObj)
        val encodedRecursiveObj = encodeToMap(recursiveObj)
        val expectedRecursiveObj = mutableMapOf("value" to mutableMapOf("value" to "foo"))
        assertThat(encodedRecursiveObj).containsExactlyEntriesIn(expectedRecursiveObj)

        val doubleGenericObj = DoubleGenericObject(valueA = "foo", valueB = 1L)
        val encodedDoubleGenericObj = encodeToMap(doubleGenericObj)
        val expectedDoubleGenericObj = mutableMapOf("valueA" to "foo", "valueB" to 1L)
        assertThat(encodedDoubleGenericObj).containsExactlyEntriesIn(expectedDoubleGenericObj)

        // TODO: Add support to encode a custom object with a generic map as field,
        //  currently it is not possible to obtain serializer for type Any at compile time
        val map = mapOf("foo" to "foo", "bar" to 1L)
        val mapObj = GenericObject(map)
        AssertThrows<IllegalArgumentException> { encodeToMap(mapObj) }
            .hasMessageThat()
            .contains("Mark the class as @Serializable or provide the serializer explicitly")
    }

    @Serializable
    private class StaticFieldBean {
        var value2: String? = null

        companion object {
            var value1 = "static-value"
                set(value) {
                    field = value + "foobar"
                }
        }
    }

    @Test
    fun `static field is not encoded`() {
        val value = StaticFieldBean()
        value.value2 = "foo"
        StaticFieldBean.value1 = "x"
        val encodedMap = encodeToMap(value)
        val expectedMap = mutableMapOf("value2" to "foo")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `setters can override parents setters in encoding`() {
        open class ConflictingSetterBean {
            open var value: Int = 1
                set(value) {
                    field = value * (-100)
                }
        }
        // unlike Java, Kotlin does not allow conflict setters to compile
        @Serializable
        class NonConflictingSetterSubBean : ConflictingSetterBean() {
            override var value: Int = -1
                set(value) {
                    field = value * (-1)
                }
        }
        val nonConflictingSetterSubBean = NonConflictingSetterSubBean()
        nonConflictingSetterSubBean.value = 10
        val encodedMap = encodeToMap(nonConflictingSetterSubBean)
        val expectedMap = mutableMapOf("value" to -10)
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `throw when recursively calling encoding`() {
        @Serializable
        class ObjectBean {
            var value: ObjectBean? = null
        }

        val objectBean = ObjectBean()
        objectBean.value = objectBean
        AssertThrows<IllegalArgumentException> { encodeToMap(objectBean) }
            .hasMessageThat()
            .contains(
                "Exceeded maximum depth of 500, which likely indicates there's an object cycle"
            )
    }

    @Test
    fun `documentReference is supported`() {
        @Serializable data class KtxDocRefObject(@Contextual val value: DocumentReference? = null)

        val docRef = documentReference("foo/bar")
        val ktxDocRefObject = KtxDocRefObject(docRef)
        val encodedMap = encodeToMap(ktxDocRefObject)
        val expectedMap = mutableMapOf("value" to docRef)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `documentReference without default value is supported`() {
        @Serializable data class KtxDocRefObject(@Contextual val value: DocumentReference)

        val docRef = documentReference("foo/bar")
        val ktxDocRefObject = KtxDocRefObject(docRef)
        val encodedMap = encodeToMap(ktxDocRefObject)
        val expectedMap = mutableMapOf("value" to docRef)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `encode Timestamp is supported`() {
        @Serializable data class ServerTimestampObject(@Contextual val value: Timestamp? = null)

        val now: Timestamp = Timestamp.now()
        val serverTimestampObject = ServerTimestampObject(now)
        val encodedMap = encodeToMap(serverTimestampObject)
        val expectedMap = mutableMapOf("value" to now)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `encode GeoPoint is supported`() {
        @Serializable data class GeoPointObject(@Contextual val value: GeoPoint? = null)

        val here = GeoPoint(88.0, 66.0)
        val geoPointObject = GeoPointObject(here)
        val encodedMap = encodeToMap(geoPointObject)
        val expectedMap = mutableMapOf("value" to here)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `field without backing field is not encoded`() {
        @Serializable
        class GetterWithoutBackingFieldOnDocumentIdBean {
            val foo: String
                get() = "doc-id" // getter only, no backing field -- not serialized
            val bar: Int = 0 // property with a backing field -- serialized
        }

        // this is different behavior than the current Java solution, Java will encode the getter
        // even without a backing field
        // While, in Kotlin, only a class's properties with backing fields are serialized.
        val expectedMap = mutableMapOf<String, Any?>("bar" to 0)
        val encodedMap = encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean())
        println(encodedMap)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `KServerTimestamp applied on wrong type should throw`() {
        @Serializable
        data class ServerTimestampObject(@Contextual @KServerTimestamp val value: String? = null)
        val serverTimestampObject = ServerTimestampObject("100")
        AssertThrows<IllegalArgumentException> { encodeToMap(serverTimestampObject) }
            .hasMessageThat()
            .contains("instead of Date or Timestamp")
    }

    @Test
    fun `test for testing AssertThrows extension function`() {
        // throw wrong type of exception
        try {
            AssertThrows<IllegalArgumentException> { -> listOf(1, 2, 3).get(100) }.hasMessageThat().contains("foobar")
        } catch (error: AssertionError) {
            assertThat(error).hasMessageThat().contains("expected:<java.lang.IllegalArgumentException> but was:<java.lang.ArrayIndexOutOfBoundsException>")
        }
        // does not throw any exception
        try {
            AssertThrows<IllegalArgumentException> { -> listOf(1, 2, 3).get(0) }.hasMessageThat().contains("foobar")
        } catch (error: AssertionError) {
            assertThat(error).hasMessageThat().contains("expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown")
        }
    }
}