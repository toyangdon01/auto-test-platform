import type { ParamDefinition, ParamOption } from '@/api/script'

/**
 * 参数契约的界面侧读取规则，与后端 TaskServiceImpl.checkParams / TaskExecutionService 保持一致。
 * 共享参数和步骤参数的默认值都会注入环境变量。
 * 键名口径：default 是规范键，defaultValue 是历史别名，两者都写时以 default 为准
 * （对应后端 PackageSchema.declaredDefault）。
 * 提交任务没带这个键时用声明值兜底，显式提交的空串按空值下发。
 */

export function declaredDefault(param: ParamDefinition): unknown {
  if (param.default !== undefined && param.default !== null) return param.default
  return param.defaultValue
}

export function hasDeclaredDefault(param: ParamDefinition): boolean {
  const value = declaredDefault(param)
  return value !== undefined && value !== null && String(value) !== ''
}

export function isRequired(param: ParamDefinition): boolean {
  return param.required === true
}

export function paramLabel(param: ParamDefinition): string {
  return param.displayName || param.name
}

/** options 允许 {label,value} 与裸字符串两种历史形状，界面展示用；取不到提交值的项不显示 */
export function optionChoices(param: ParamDefinition): ParamOption[] {
  const raw = param.options
  if (!Array.isArray(raw)) return []
  const choices: ParamOption[] = []
  for (const item of raw) {
    if (typeof item === 'string') {
      if (item.trim() !== '') choices.push({ label: item, value: item })
      continue
    }
    if (!item) continue
    const value = item.value ?? item.label
    if (value === undefined || value === null || String(value) === '') continue
    choices.push({ label: String(item.label ?? value), value: String(value) })
  }
  return choices
}

/**
 * 是否声明了白名单。与后端 `!options.isEmpty()` 逐字对齐：数组存在且非空即算声明，
 * 哪怕里面全是要被丢弃的无效项 —— 那种包会让所有取值都被拒，界面不能假装没这回事。
 */
export function hasOptionWhitelist(param: ParamDefinition): boolean {
  return Array.isArray(param.options) && (param.options as unknown[]).length > 0
}

/** 白名单里真正参与匹配的取值，与后端 TaskServiceImpl.allowedValues 一致（value 为 null 才回落 label） */
export function allowedOptionValues(param: ParamDefinition): string[] {
  const raw = param.options
  if (!Array.isArray(raw)) return []
  const values: string[] = []
  for (const item of raw) {
    if (typeof item === 'string') {
      if (item.trim() !== '') values.push(item.trim())
      continue
    }
    if (!item) continue
    const value = item.value ?? item.label
    if (value === undefined || value === null || String(value).trim() === '') continue
    values.push(String(value).trim())
  }
  return values
}

export interface ParamProblem {
  code: 'PARAM_REQUIRED' | 'PARAM_NOT_IN_OPTIONS'
  param: string
  step?: string
  message: string
  allowed?: string[]
}

function filled(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function checkOne(param: ParamDefinition, effective: unknown, hasValue: boolean, where: string, step?: string): ParamProblem | null {
  if (isRequired(param) && !hasValue) {
    return {
      code: 'PARAM_REQUIRED',
      param: param.name,
      step,
      message: `${where} 是必填项但未填写`,
    }
  }
  if (hasValue && hasOptionWhitelist(param) && !allowedOptionValues(param).includes(String(effective).trim())) {
    const allowed = allowedOptionValues(param)
    return {
      code: 'PARAM_NOT_IN_OPTIONS',
      param: param.name,
      step,
      message: `${where} 的取值 ${effective} 不在可选值 [${allowed.join(', ')}] 内`,
      allowed
    }
  }
  return null
}

/** 共享参数：生效值 = 提交上来的取值，没提交这个键时用声明的 default 兜底 */
export function checkSharedParams(params: ParamDefinition[], values: Record<string, unknown>): ParamProblem[] {
  const problems: ParamProblem[] = []
  for (const param of params) {
    if (!param.name || param.name === '_BACKGROUND') continue
    const value = values ? values[param.name] : undefined
    // 与后端 TaskServiceImpl.checkParams 一致：显式提交的空串算「提交了」，不会被声明值盖掉
    const effective = value === undefined || value === null ? declaredDefault(param) : value
    const problem = checkOne(param, effective, filled(effective), `参数 ${paramLabel(param)}`)
    if (problem) problems.push(problem)
  }
  return problems
}

/** 步骤参数：生效值 = 用户填写 > 步骤声明默认值 > 共享参数取值（shared 传入时已含其声明默认值） */
export function checkStepParams(
  params: ParamDefinition[],
  values: Record<string, unknown>,
  shared: Record<string, unknown>,
  stepName?: string
): ParamProblem[] {
  const problems: ParamProblem[] = []
  const prefix = stepName ? `步骤 ${stepName} 的参数 ` : '参数 '
  for (const param of params) {
    if (!param.name || param.name === '_BACKGROUND') continue
    const supplied = values ? values[param.name] : undefined
    let effective = supplied
    if (effective === undefined || effective === null) {
      const declared = declaredDefault(param)
      // 与后端 effectiveStepValue 一致：只有步骤压根没声明默认值时才回退到共享取值，
      // 声明成空串也算声明，会盖掉共享取值
      effective = declared !== undefined && declared !== null ? declared : (shared ? shared[param.name] : undefined)
    }
    const problem = checkOne(param, effective, filled(effective), prefix + paramLabel(param), stepName)
    if (problem) problems.push(problem)
  }
  return problems
}

/**
 * 某个共享参数是否会被任一步骤的同名默认值盖掉。
 * 后端优先级是 用户步骤取值 > 步骤默认值 > 共享取值，界面必须提示，否则使用者会以为共享框里填的值生效。
 */
export function shadowedBySteps(stepGroups: Array<{ stepName: string; params: ParamDefinition[] }>, name: string) {
  return stepGroups.filter(group => group.params.some(p => p.name === name && hasDeclaredDefault(p)))
}
