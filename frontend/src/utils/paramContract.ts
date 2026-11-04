import type { ParamDefinition, ParamOption } from '@/api/script'

/**
 * 参数契约的界面侧读取规则，与后端 TaskServiceImpl.checkParams / TaskExecutionService 保持一致。
 * 共享参数的默认值键是 default 且执行时不会注入；步骤参数是 defaultValue（后端也接受 default），会注入。
 */

export function declaredDefault(param: ParamDefinition): unknown {
  if (param.defaultValue !== undefined && param.defaultValue !== null) return param.defaultValue
  return param.default
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

/** options 允许 {label,value} 与裸字符串两种历史形状 */
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

export interface ParamProblem {
  code: 'PARAM_REQUIRED' | 'PARAM_NOT_IN_OPTIONS'
  param: string
  step?: string
  message: string
}

function filled(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function checkOne(param: ParamDefinition, effective: unknown, hasValue: boolean, where: string, step?: string): ParamProblem | null {
  const choices = optionChoices(param)
  if (isRequired(param) && !hasValue) {
    const tail = step === undefined && hasDeclaredDefault(param) ? '（脚本声明的默认值不会自动注入）' : ''
    return {
      code: 'PARAM_REQUIRED',
      param: param.name,
      step,
      message: `${where} 是必填项但未填写${tail}`,
    }
  }
  if (hasValue && choices.length > 0 && !choices.some(c => c.value === String(effective).trim())) {
    return {
      code: 'PARAM_NOT_IN_OPTIONS',
      param: param.name,
      step,
      message: `${where} 的取值 ${effective} 不在可选值 [${choices.map(c => c.value).join(', ')}] 内`,
    }
  }
  return null
}

/** 共享参数：只看提交上来的取值，声明的 default 不算满足必填 */
export function checkSharedParams(params: ParamDefinition[], values: Record<string, unknown>): ParamProblem[] {
  const problems: ParamProblem[] = []
  for (const param of params) {
    if (!param.name || param.name === '_BACKGROUND') continue
    const value = values ? values[param.name] : undefined
    const problem = checkOne(param, value, filled(value), `参数 ${paramLabel(param)}`)
    if (problem) problems.push(problem)
  }
  return problems
}

/** 步骤参数：生效值 = 用户填写 > 步骤声明默认值 > 共享参数取值 */
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
