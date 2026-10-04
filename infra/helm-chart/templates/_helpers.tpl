{{/*
Naming and label helpers, shared by every template in this chart.

Two names, and the difference matters. `crm.name` is the chart name, used where a
Kubernetes object name must not collide across releases. `crm.fullname` is the
release-qualified name, used for the objects this chart owns. Getting this wrong
produces two releases fighting over one Deployment, which is the failure mode a
trunk-based project hits the moment two environments exist.
*/}}

{{- define "crm.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "crm.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "crm.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "crm.namespace" -}}
{{- .Release.Namespace -}}
{{- end -}}

{{- define "crm.labels" -}}
helm.sh/chart: {{ include "crm.chart" . }}
app.kubernetes.io/name: {{ include "crm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: crm
{{- with .Values.global.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{/*
Selector labels. Deliberately not include the version or the chart name: they are
part of the identity and must not change, or a Helm upgrade would look like a
new Deployment with no pods matching it, and the rollout would hang on a selector
that matches nothing.
*/}}
{{- define "crm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "crm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
A full image reference. Centralised so that changing the registry or the tag is a
one-line change in values.yaml rather than eight edits in a template, which is
the failure a copy-pasted image line always eventually produces.
*/}}
{{- define "crm.image" -}}
{{- $registry := .root.Values.global.imageRegistry -}}
{{- $tag := .root.Values.global.imageTag | toString -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry .name $tag -}}
{{- else -}}
{{- printf "%s:%s" .name $tag -}}
{{- end -}}
{{- end -}}

{{- define "crm.imagePullSecrets" -}}
{{- with .Values.global.imagePullSecret }}
imagePullSecrets:
  - name: {{ . }}
{{- end }}
{{- end -}}

{{/*
The scheduling keys, emitted only when set. Emitting an empty nodeSelector or an
empty tolerations list is legal but reads as if a decision had been made, so they
are omitted when empty.
*/}}
{{- define "crm.scheduling" -}}
{{- with .Values.nodeSelector }}
nodeSelector:
{{ toYaml . | indent 2 }}
{{- end }}
{{- with .Values.tolerations }}
tolerations:
{{ toYaml . | indent 2 }}
{{- end }}
{{- with .Values.affinity }}
affinity:
{{ toYaml . | indent 2 }}
{{- end }}
{{- end -}}

{{/*
The environment variables of a backend service, from the values list.

Each entry carries either a literal `value`, or a `secretKey` / `configMapKey`
saying where to read the real one from. Rendering this in the chart rather than
writing seven copies of the same env block is the whole point of the chart: the
separation between what is secret and what is not is expressed once, and a
service cannot accidentally get a credential in clear text because the template
does not have a code path for it.
*/}}
{{- define "crm.serviceEnv" -}}
{{- $root := .root -}}
{{- range .service.env }}
{{- if hasKey . "value" }}
- name: {{ .name }}
  value: {{ .value | quote }}
{{- else if hasKey . "configMapKey" }}
- name: {{ .name }}
  valueFrom:
    configMapKeyRef:
      name: {{ $root.Values.config.name | default (printf "%s-config" (include "crm.fullname" $root)) }}
      key: {{ .configMapKey }}
{{- else if hasKey . "secretKey" }}
- name: {{ .name }}
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.secrets.name }}
      key: {{ .secretKey }}
      {{- if .optional }}
      optional: true
      {{- end }}
{{- else }}
{{- fail (printf "backendServices env entry %q for a service has none of value, configMapKey or secretKey" .name) }}
{{- end }}
{{- end }}
{{- end -}}

{{/*
The probes a backend service uses. The values may override the delays; the probe
type stays a socket, because the chart does not know which services carry the
actuator and guessing would produce probes that fail for the wrong reason.

These are TCP sockets rather than HTTP health checks: only service-discovery
carries the actuator starter, so /actuator/health answers there and nowhere
else. An HTTP probe on the other six would fail for a reason that has nothing to
do with whether they are healthy. A socket probe proves the port is being
accepted, which is weaker and is said out loud in the README.
*/}}
{{- define "crm.backendProbes" -}}
readinessProbe:
  tcpSocket:
    port: {{ .port }}
  initialDelaySeconds: {{ .readiness.initialDelaySeconds | default 40 }}
  periodSeconds: {{ .readiness.periodSeconds | default 10 }}
livenessProbe:
  tcpSocket:
    port: {{ .port }}
  initialDelaySeconds: {{ .liveness.initialDelaySeconds | default 90 }}
  periodSeconds: {{ .liveness.periodSeconds | default 20 }}
{{- end -}}
