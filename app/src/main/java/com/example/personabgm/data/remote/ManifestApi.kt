package com.example.personabgm.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface ManifestApi {
    /**
     * Fetches the manifest from an absolute URL. Pass the cached ETag as [ifNoneMatch] so an
     * unchanged manifest answers 304 with no body. A null header value is omitted by Retrofit.
     */
    @GET
    suspend fun fetchManifest(
        @Url url: String,
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<ManifestDto>
}
