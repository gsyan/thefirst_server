import os
import re
import subprocess
import platform

# C# → Java 타입 매핑
CS_TO_JAVA_TYPE = {
    'string': 'String',
    'int':    'Integer',
    'float':  'Float',
    'double': 'Double',
    'long':   'Long',
    'bool':   'Boolean',
}

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")


def cs_type_to_java(cs_type):
    """C# 타입 → Java 타입 변환 (primitive + complex)"""
    if cs_type in CS_TO_JAVA_TYPE:
        return CS_TO_JAVA_TYPE[cs_type]
    # List<T>
    list_match = re.match(r'List<(\w+)>', cs_type)
    if list_match:
        return f'List<{cs_type_to_java(list_match.group(1))}>'
    # enum (E 접두사) — com.bk.sbs.enums에 generate_common_define.py로 이미 생성되어 있다고 가정
    if cs_type.startswith('E'):
        return cs_type
    # Request/Response는 그대로
    if cs_type.endswith('Request') or cs_type.endswith('Response'):
        return cs_type
    # 그 외 복합 타입 → Dto suffix (재귀적으로 별도 파일 생성 대상)
    return f'{cs_type}Dto'


def extract_complex_type_names(java_type):
    """java_type(가능하면 List<...> 중첩)에서 '...Dto'로 끝나는 원본 C# 타입 이름들을 모두 추출"""
    names = []
    for m in re.finditer(r'(\w+)Dto', java_type):
        names.append(m.group(1))
    return names


def parse_server_fields_from_csharp(cs_file_path, class_name):
    """C# 소스에서 class_name 클래스의 // [server] 마커가 붙은 public 필드를 추출"""
    with open(cs_file_path, 'r', encoding='utf-8') as f:
        source = f.read()

    # class_name 클래스 블록 추출
    pattern = rf'public class {re.escape(class_name)}\s*\{{(.*?)\n\}}'
    match = re.search(pattern, source, re.DOTALL)
    if not match:
        raise ValueError(f"Class '{class_name}' not found in {cs_file_path}")

    class_body = match.group(1)

    # public <type> <name> ... // [server] — 모든 타입 처리
    field_pattern = re.compile(
        r'public\s+([\w<>]+)\s+(\w+)[^;]*;\s*//\s*\[server\]',
        re.MULTILINE
    )

    fields = []
    for m in field_pattern.finditer(class_body):
        cs_type, field_name = m.group(1), m.group(2)
        java_type = cs_type_to_java(cs_type)
        fields.append({'name': field_name, 'type': java_type})

    return fields


def parse_all_fields_from_csharp(source, class_name):
    """C# 소스에서 class_name 클래스의 public 필드를 전부(마커 무관) 추출 — 중첩 Dto용 리프 타입 파싱"""
    pattern = rf'public class {re.escape(class_name)}\s*\{{(.*?)\n\}}'
    match = re.search(pattern, source, re.DOTALL)
    if not match:
        raise ValueError(f"Class '{class_name}' not found while resolving nested Dto")

    class_body = match.group(1)

    field_pattern = re.compile(
        r'public\s+([\w<>]+)\s+(\w+)\s*;',
        re.MULTILINE
    )

    fields = []
    for m in field_pattern.finditer(class_body):
        cs_type, field_name = m.group(1), m.group(2)
        java_type = cs_type_to_java(cs_type)
        fields.append({'name': field_name, 'type': java_type})

    return fields


def generate_nested_dto(class_name, java_type_fields, output_dir, package_name):
    """중첩 복합 타입 하나에 대한 Dto java 파일 생성"""
    java_class_name = f"{class_name}Dto"
    needs_list = any('List<' in f['type'] for f in java_type_fields)

    imports = {'import lombok.AllArgsConstructor;', 'import lombok.Builder;', 'import lombok.Data;', 'import lombok.NoArgsConstructor;'}
    if needs_list:
        imports.add('import java.util.List;')
    for f in java_type_fields:
        enum_match = re.search(r'(\bE[A-Z]\w*)', f['type'])
        if enum_match:
            imports.add(f"import com.bk.sbs.enums.{enum_match.group(1)};")

    java_code  = f"package {package_name};\n\n"
    for imp in sorted(imports):
        java_code += f"{imp}\n"
    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {java_class_name}\n"
    java_code += f" * Auto-generated from Unity C# {class_name} class (nested type referenced by ZoneConfig)\n"
    java_code += " */\n"
    java_code += "@Data\n@NoArgsConstructor\n@Builder\n@AllArgsConstructor\n"
    java_code += f"public class {java_class_name} {{\n"
    for field in java_type_fields:
        java_code += f"    private {field['type']} {field['name']};\n"
    java_code += "}\n"

    output_file_path = os.path.join(output_dir, f"{java_class_name}.java")
    os.makedirs(output_dir, exist_ok=True)
    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(java_code)

    print(f"Generated (nested): {output_file_path}")
    print(f"  Fields: {', '.join(f['name'] for f in java_type_fields)}")
    return output_file_path


def generate_nested_dtos_recursively(cs_source_path, top_level_fields, output_dir, package_name):
    """top_level_fields에서 참조하는 '...Dto' 복합 타입들을 재귀적으로 전부 생성"""
    with open(cs_source_path, 'r', encoding='utf-8') as f:
        source = f.read()

    visited = set()
    pending = []
    for f in top_level_fields:
        pending.extend(extract_complex_type_names(f['type']))

    generated_files = []
    while pending:
        class_name = pending.pop()
        if class_name in visited:
            continue
        visited.add(class_name)

        nested_fields = parse_all_fields_from_csharp(source, class_name)
        generated_files.append(generate_nested_dto(class_name, nested_fields, output_dir, package_name))

        for nf in nested_fields:
            pending.extend(extract_complex_type_names(nf['type']))

    return generated_files


def generate_zone_config_dto(cs_source_path, output_dir, package_name):
    """C# ZoneConfig에서 [server] 마커 필드를 읽어 ZoneConfigData.java 생성 + 참조하는 복합 타입도 재귀 생성"""

    server_fields = parse_server_fields_from_csharp(cs_source_path, 'ZoneConfig')
    if not server_fields:
        raise ValueError("No [server] fields found in ZoneConfig. Check // [server] markers in DataTableZone.cs")

    java_class_name = "ZoneConfigData"

    needs_list = any('List<' in f['type'] for f in server_fields)

    imports = {'import lombok.AllArgsConstructor;', 'import lombok.Builder;', 'import lombok.Data;', 'import lombok.NoArgsConstructor;'}
    if needs_list:
        imports.add('import java.util.List;')
    for f in server_fields:
        enum_match = re.search(r'(\bE[A-Z]\w*)', f['type'])
        if enum_match:
            imports.add(f"import com.bk.sbs.enums.{enum_match.group(1)};")

    java_code  = f"package {package_name};\n\n"
    for imp in sorted(imports):
        java_code += f"{imp}\n"
    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {java_class_name}\n"
    java_code += " * Auto-generated from Unity C# ZoneConfig class (server-required fields only)\n"
    java_code += " */\n"
    java_code += "@Data\n"
    java_code += "@NoArgsConstructor\n"
    java_code += "@Builder\n"
    java_code += "@AllArgsConstructor\n"
    java_code += f"public class {java_class_name} {{\n"

    for field in server_fields:
        java_code += f"    private {field['type']} {field['name']};\n"

    java_code += "}\n"

    output_file_path = os.path.join(output_dir, f"{java_class_name}.java")
    os.makedirs(output_dir, exist_ok=True)

    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(java_code)

    print(f"Generated: {output_file_path}")
    print(f"  Fields: {', '.join(f['name'] for f in server_fields)}")

    nested_generated = generate_nested_dtos_recursively(cs_source_path, server_fields, output_dir, package_name)

    return output_file_path, nested_generated


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # C# 소스 경로 (클라 프로젝트 기준 상대 경로)
    cs_source_path = os.path.join(
        script_dir,
        r"../../../thefirst_client_unity/Assets/Scripts/System/Data/DataTableZone.cs"
    )

    output_dir   = os.path.join(script_dir, r"../../src/main/java/com/bk/sbs/dto")
    package_name = "com.bk.sbs.dto"

    print("Generating ZoneConfigData (parsed from C# // [server] markers)")
    print("="*55)
    print(f"Source: {os.path.abspath(cs_source_path)}")

    output_file, nested_generated = generate_zone_config_dto(cs_source_path, output_dir, package_name)

    if nested_generated:
        print(f"\nNested Dto generated: {len(nested_generated)}")

    open_file_location(output_file)
