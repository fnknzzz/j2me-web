#ifndef M3G_CORE_INCLUDE
#   error included by m3g_core.c; do not compile separately.
#endif

static M3Gshort m3gWasmSkinningFloatEncode(M3Gfloat value,
                                            M3Gfloat bias,
                                            M3Gfloat scale)
{
    M3Gfloat encoded;

    if (scale <= 0.f) {
        return 0;
    }
    encoded = (value - bias) / scale;
    if (encoded > 32767.f) {
        encoded = 32767.f;
    }
    else if (encoded < -32768.f) {
        encoded = -32768.f;
    }
    return (M3Gshort) m3gRoundToInt(encoded);
}

static M3Gbool m3gWasmSkinningFloatReadVector(
    const VertexArray *array,
    const void *data,
    M3Gint vertexIndex,
    M3Gfloat *coordinates)
{
    switch (array->elementType) {
    case M3G_GLTYPE(M3G_BYTE):
    {
        const M3Gbyte *values = (const M3Gbyte*)data + vertexIndex * 4;
        coordinates[0] = values[0];
        coordinates[1] = values[1];
        coordinates[2] = values[2];
        return M3G_TRUE;
    }
    case M3G_GLTYPE(M3G_UBYTE):
    {
        const M3Gubyte *values = (const M3Gubyte*)data + vertexIndex * 4;
        coordinates[0] = values[0];
        coordinates[1] = values[1];
        coordinates[2] = values[2];
        return M3G_TRUE;
    }
    case M3G_GLTYPE(M3G_SHORT):
    {
        const M3Gshort *values = (const M3Gshort*)data + vertexIndex * 3;
        coordinates[0] = values[0];
        coordinates[1] = values[1];
        coordinates[2] = values[2];
        return M3G_TRUE;
    }
    case M3G_GLTYPE(M3G_USHORT):
    {
        const M3Gushort *values = (const M3Gushort*)data + vertexIndex * 3;
        coordinates[0] = values[0];
        coordinates[1] = values[1];
        coordinates[2] = values[2];
        return M3G_TRUE;
    }
    default:
        return M3G_FALSE;
    }
}

static M3Gbool m3gWasmSkinningFloatComputeBoneTransforms(
    SkinnedMesh *mesh,
    Matrix *transforms,
    Matrix *normalTransforms)
{
    M3Gint boneCount = m3gArraySize(&mesh->bones);
    M3Gint boneIndex;

    for (boneIndex = 0; boneIndex < boneCount; ++boneIndex) {
        Bone *bone = m3gGetArrayElement(&mesh->bones, boneIndex);
        if (!m3gGetTransformTo(bone->node, (Node*) mesh, &transforms[boneIndex])) {
            return M3G_FALSE;
        }
        m3gMulMatrix(&transforms[boneIndex], &bone->toBone);
        if (normalTransforms != NULL
            && !m3gInverseTranspose(&normalTransforms[boneIndex],
                                    &transforms[boneIndex])) {
            m3gRaiseError(M3G_INTERFACE(mesh), M3G_ARITHMETIC_ERROR);
            return M3G_FALSE;
        }
    }
    return M3G_TRUE;
}

static void m3gWasmSkinningFloatBlendPosition(const SkinnedMesh *mesh,
                                               const Matrix *transforms,
                                               M3Gint vertexIndex,
                                               const Vec4 *source,
                                               Vec3 *out)
{
    M3Gint influenceIndex;
    M3Gfloat totalWeight = 0.f;

    out->x = 0.f;
    out->y = 0.f;
    out->z = 0.f;
    for (influenceIndex = 0;
         influenceIndex < mesh->bonesPerVertex;
         ++influenceIndex) {
        M3Gubyte normalizedWeight =
            mesh->normalizedWeights[influenceIndex][vertexIndex];
        if (normalizedWeight != 0) {
            M3Gubyte boneIndex =
                mesh->boneIndices[influenceIndex][vertexIndex];
            Vec4 transformed = *source;
            M3Gfloat weight = normalizedWeight / 128.f;

            m3gTransformVec4(&transforms[boneIndex], &transformed);
            out->x += transformed.x * weight;
            out->y += transformed.y * weight;
            out->z += transformed.z * weight;
            totalWeight += weight;
        }
    }
    if (totalWeight > 0.f) {
        M3Gfloat inverseWeight = 1.f / totalWeight;
        out->x *= inverseWeight;
        out->y *= inverseWeight;
        out->z *= inverseWeight;
    }
    else {
        out->x = source->x;
        out->y = source->y;
        out->z = source->z;
    }
}

static void m3gWasmSkinningFloatBlendNormal(const SkinnedMesh *mesh,
                                             const Matrix *transforms,
                                             M3Gint vertexIndex,
                                             const Vec4 *source,
                                             Vec3 *out)
{
    M3Gint influenceIndex;
    M3Gfloat totalWeight = 0.f;

    out->x = 0.f;
    out->y = 0.f;
    out->z = 0.f;
    for (influenceIndex = 0;
         influenceIndex < mesh->bonesPerVertex;
         ++influenceIndex) {
        M3Gubyte normalizedWeight =
            mesh->normalizedWeights[influenceIndex][vertexIndex];
        if (normalizedWeight != 0) {
            M3Gubyte boneIndex =
                mesh->boneIndices[influenceIndex][vertexIndex];
            Vec4 transformed = *source;
            M3Gfloat weight = normalizedWeight / 128.f;

            m3gTransformVec4(&transforms[boneIndex], &transformed);
            out->x += transformed.x * weight;
            out->y += transformed.y * weight;
            out->z += transformed.z * weight;
            totalWeight += weight;
        }
    }
    if (totalWeight > 0.f) {
        M3Gfloat inverseWeight = 1.f / totalWeight;
        out->x *= inverseWeight;
        out->y *= inverseWeight;
        out->z *= inverseWeight;
        m3gNormalizeVec3(out);
    }
    else {
        out->x = source->x;
        out->y = source->y;
        out->z = source->z;
    }
}

static M3Gbool m3gWasmSkinningFloatMorph(SkinnedMesh *mesh)
{
    const VertexBuffer *sourceBuffer = mesh->mesh.vertexBuffer;
    VertexBuffer *destinationBuffer = mesh->morphedVB;
    const M3Gint vertexCount = m3gGetNumVertices(sourceBuffer);
    const M3Gint boneCount = m3gArraySize(&mesh->bones);
    const M3Gbool hasNormals = sourceBuffer->normals != NULL;
    Matrix *transforms;
    Matrix *normalTransforms;
    const void *sourcePositions;
    const void *sourceNormals = NULL;
    M3Gfloat *positions;
    M3Gfloat minPosition[3];
    M3Gfloat maxPosition[3];
    M3Gfloat maxExtent;
    M3Gfloat scale;
    M3Gshort *destinationPositions;
    M3Gbyte *destinationNormals = NULL;
    M3Gint vertexIndex;

    if (vertexCount <= 0 || sourceBuffer->vertices == NULL
        || destinationBuffer->vertices == NULL) {
        return M3G_FALSE;
    }
    if (mesh->weightsDirty) {
        m3gNormalizeWeights(mesh);
    }
    transforms = m3gAllocTemp(
        M3G_INTERFACE(mesh),
        (M3Gsizei)boneCount * sizeof(Matrix)
        + (hasNormals ? (M3Gsizei)boneCount * sizeof(Matrix) : 0)
        + (M3Gsizei)vertexCount * 3 * sizeof(M3Gfloat));
    if (transforms == NULL) {
        return M3G_FALSE;
    }
    normalTransforms = hasNormals ? transforms + boneCount : NULL;
    positions = (M3Gfloat*)(hasNormals
        ? normalTransforms + boneCount
        : transforms + boneCount);
    sourcePositions = m3gMapVertexArrayReadOnly(sourceBuffer->vertices);
    if (sourcePositions == NULL) {
        m3gFreeTemp(M3G_INTERFACE(mesh));
        return M3G_FALSE;
    }
    if (hasNormals) {
        sourceNormals = m3gMapVertexArrayReadOnly(sourceBuffer->normals);
        if (sourceNormals == NULL) {
            m3gUnmapVertexArray(sourceBuffer->vertices);
            m3gFreeTemp(M3G_INTERFACE(mesh));
            return M3G_FALSE;
        }
    }
    if (!m3gWasmSkinningFloatComputeBoneTransforms(
            mesh,
            transforms,
            normalTransforms)) {
        if (sourceNormals != NULL) {
            m3gUnmapVertexArray(sourceBuffer->normals);
        }
        m3gUnmapVertexArray(sourceBuffer->vertices);
        m3gFreeTemp(M3G_INTERFACE(mesh));
        return M3G_FALSE;
    }

    minPosition[0] = minPosition[1] = minPosition[2] = M3G_MAX_POSITIVE_FLOAT;
    maxPosition[0] = maxPosition[1] = maxPosition[2] = M3G_MIN_NEGATIVE_FLOAT;
    for (vertexIndex = 0; vertexIndex < vertexCount; ++vertexIndex) {
        M3Gfloat coordinates[3];
        Vec4 source;
        Vec3 skinned;
        M3Gint component;

        if (!m3gWasmSkinningFloatReadVector(
                sourceBuffer->vertices, sourcePositions, vertexIndex, coordinates)) {
            if (sourceNormals != NULL) {
                m3gUnmapVertexArray(sourceBuffer->normals);
            }
            m3gUnmapVertexArray(sourceBuffer->vertices);
            m3gFreeTemp(M3G_INTERFACE(mesh));
            return M3G_FALSE;
        }
        source.x = coordinates[0] * sourceBuffer->vertexScale
            + sourceBuffer->vertexBias[0];
        source.y = coordinates[1] * sourceBuffer->vertexScale
            + sourceBuffer->vertexBias[1];
        source.z = coordinates[2] * sourceBuffer->vertexScale
            + sourceBuffer->vertexBias[2];
        source.w = 1.f;
        m3gWasmSkinningFloatBlendPosition(
            mesh, transforms, vertexIndex, &source, &skinned);
        positions[vertexIndex * 3 + 0] = skinned.x;
        positions[vertexIndex * 3 + 1] = skinned.y;
        positions[vertexIndex * 3 + 2] = skinned.z;
        for (component = 0; component < 3; ++component) {
            minPosition[component] =
                M3G_MIN(minPosition[component], (&skinned.x)[component]);
            maxPosition[component] =
                M3G_MAX(maxPosition[component], (&skinned.x)[component]);
        }
    }

    maxExtent = 0.f;
    for (vertexIndex = 0; vertexIndex < 3; ++vertexIndex) {
        destinationBuffer->vertexBias[vertexIndex] = m3gMul(
            0.5f,
            m3gAdd(minPosition[vertexIndex], maxPosition[vertexIndex]));
        maxExtent = M3G_MAX(
            maxExtent,
            m3gAbs(m3gSub(
                maxPosition[vertexIndex],
                destinationBuffer->vertexBias[vertexIndex])));
        maxExtent = M3G_MAX(
            maxExtent,
            m3gAbs(m3gSub(
                minPosition[vertexIndex],
                destinationBuffer->vertexBias[vertexIndex])));
    }
    scale = maxExtent > 0.f ? maxExtent / 32767.f : 1.f;
    destinationBuffer->vertexScale = scale;

    destinationPositions = (M3Gshort*)m3gMapVertexArray(
        destinationBuffer->vertices);
    if (destinationPositions == NULL) {
        if (sourceNormals != NULL) {
            m3gUnmapVertexArray(sourceBuffer->normals);
        }
        m3gUnmapVertexArray(sourceBuffer->vertices);
        m3gFreeTemp(M3G_INTERFACE(mesh));
        return M3G_FALSE;
    }
    for (vertexIndex = 0; vertexIndex < vertexCount; ++vertexIndex) {
        destinationPositions[vertexIndex * 3 + 0] =
            m3gWasmSkinningFloatEncode(
                positions[vertexIndex * 3 + 0],
                destinationBuffer->vertexBias[0],
                scale);
        destinationPositions[vertexIndex * 3 + 1] =
            m3gWasmSkinningFloatEncode(
                positions[vertexIndex * 3 + 1],
                destinationBuffer->vertexBias[1],
                scale);
        destinationPositions[vertexIndex * 3 + 2] =
            m3gWasmSkinningFloatEncode(
                positions[vertexIndex * 3 + 2],
                destinationBuffer->vertexBias[2],
                scale);
    }
    m3gUnmapVertexArray(destinationBuffer->vertices);

    if (hasNormals && destinationBuffer->normals != NULL) {
        destinationNormals = (M3Gbyte*)m3gMapVertexArray(
            destinationBuffer->normals);
        if (destinationNormals == NULL) {
            m3gUnmapVertexArray(sourceBuffer->normals);
            m3gUnmapVertexArray(sourceBuffer->vertices);
            m3gFreeTemp(M3G_INTERFACE(mesh));
            return M3G_FALSE;
        }
        for (vertexIndex = 0; vertexIndex < vertexCount; ++vertexIndex) {
            M3Gfloat coordinates[3];
            Vec4 source;
            Vec3 skinned;

            if (!m3gWasmSkinningFloatReadVector(
                    sourceBuffer->normals, sourceNormals, vertexIndex, coordinates)) {
                m3gUnmapVertexArray(destinationBuffer->normals);
                m3gUnmapVertexArray(sourceBuffer->normals);
                m3gUnmapVertexArray(sourceBuffer->vertices);
                m3gFreeTemp(M3G_INTERFACE(mesh));
                return M3G_FALSE;
            }
            source.x = coordinates[0];
            source.y = coordinates[1];
            source.z = coordinates[2];
            source.w = 0.f;
            m3gWasmSkinningFloatBlendNormal(
                mesh, normalTransforms, vertexIndex, &source, &skinned);
            destinationNormals[vertexIndex * 4 + 0] =
                (M3Gbyte)m3gRoundToInt(skinned.x * 127.f);
            destinationNormals[vertexIndex * 4 + 1] =
                (M3Gbyte)m3gRoundToInt(skinned.y * 127.f);
            destinationNormals[vertexIndex * 4 + 2] =
                (M3Gbyte)m3gRoundToInt(skinned.z * 127.f);
            destinationNormals[vertexIndex * 4 + 3] = 0;
        }
        m3gUnmapVertexArray(destinationBuffer->normals);
    }
    if (sourceNormals != NULL) {
        m3gUnmapVertexArray(sourceBuffer->normals);
    }
    m3gUnmapVertexArray(sourceBuffer->vertices);
    m3gFreeTemp(M3G_INTERFACE(mesh));
    return M3G_TRUE;
}
