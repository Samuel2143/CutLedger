#!/usr/bin/env python3
"""Resolve immutable Docker Hub manifests without requiring daemon privileges."""
import json
import urllib.request
from localenv import ROOT

images = ['node:24.22.2-bookworm-slim', 'maven:3.9.16-eclipse-temurin-17', 'eclipse-temurin:17-jre-jammy', 'postgres:17.11-alpine']
resolved = {}
for image in images:
    repo, tag = image.split(':')
    repo = 'library/'+repo
    with urllib.request.urlopen('https://auth.docker.io/token?service=registry.docker.io&scope=repository:'+repo+':pull', timeout=20) as response:
        token = json.load(response)['token']
    request = urllib.request.Request('https://registry-1.docker.io/v2/'+repo+'/manifests/'+tag, headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        digest = response.headers['Docker-Content-Digest']
        manifest = json.load(response)
    resolved[image] = {'reference':image+'@'+digest,'digest':digest,'mediaType':manifest.get('mediaType')}
(ROOT / 'docs/evidence/container-images.json').write_text(json.dumps(resolved,indent=2)+'\n')
for image, data in resolved.items():
    print(data['reference'])
