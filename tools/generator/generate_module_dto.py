import re
import os
import subprocess
import platform

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":  # macOS
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)
        else:
            print(f"Unsupported operating system: {system}")
            return

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")
        print(f"File location: {os.path.abspath(file_path)}")

def map_csharp_type_to_java(csharp_type):
    """C# 타입을 Java 타입으로 매핑"""
    type_mapping = {
        'int': 'Integer',
        'float': 'Float',
        'double': 'Double',
        'bool': 'Boolean',
        'string': 'String',
        'long': 'Long',
        'short': 'Short',
        'byte': 'Byte',
    }

    # enum 타입 (E로 시작하는 타입)은 그대로 유지
    if csharp_type.startswith('E'):
        return csharp_type

    # 기본 타입 매핑
    mapped_type = type_mapping.get(csharp_type, csharp_type)

    # 커스텀 타입 (DTO 클래스)에는 Dto 접미사 추가
    # 기본 타입(Integer, String 등)이 아닌 경우
    if mapped_type == csharp_type and csharp_type not in type_mapping:
        # Request/Response는 Dto 불필요
        if csharp_type.endswith('Request') or csharp_type.endswith('Response'):
            return csharp_type
        return f"{csharp_type}Dto"

    return mapped_type

def extract_class_fields(csharp_content, class_name):
    """C# 클래스에서 public 필드 추출"""
    # 클래스 정의 찾기
    class_pattern = rf'public\s+class\s+{class_name}\s*\{{(.*?)\n\}}'
    class_match = re.search(class_pattern, csharp_content, re.DOTALL)

    if not class_match:
        print(f"Class {class_name} not found")
        return []

    class_body = class_match.group(1)

    # public 필드 추출 (Unity attributes와 #if 블록 제외)
    fields = []
    lines = class_body.split('\n')

    skip_until_endif = False
    for line in lines:
        stripped = line.strip()

        # #if UNITY_EDITOR 블록 건너뛰기
        if stripped.startswith('#if'):
            skip_until_endif = True
            continue
        if stripped.startswith('#endif'):
            skip_until_endif = False
            continue
        if skip_until_endif:
            continue

        # Unity attributes 건너뛰기 ([Header], [Range], [TextArea] 등)
        if stripped.startswith('['):
            continue

        # 주석 건너뛰기
        if stripped.startswith('//'):
            continue

        # public 필드 찾기
        field_pattern = r'public\s+(\w+)\s+(m_\w+)\s*=?\s*[^;]*;'
        field_match = re.match(field_pattern, stripped)

        if field_match:
            field_type = field_match.group(1)
            field_name = field_match.group(2)

            # m_ 접두사 제거하고 camelCase로 변환
            java_field_name = field_name[2:]  # m_ 제거
            java_field_name = java_field_name[0].lower() + java_field_name[1:]  # 첫 글자 소문자

            # C# 타입을 Java 타입으로 매핑
            java_type = map_csharp_type_to_java(field_type)

            fields.append({
                'csharp_name': field_name,
                'java_name': java_field_name,
                'java_type': java_type
            })

    return fields

def generate_java_dto(csharp_file_path, output_dir, package_name, class_name):
    """C# 클래스에서 Java DTO 생성"""
    # C# 파일 읽기
    with open(csharp_file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # 필드 추출
    fields = extract_class_fields(content, class_name)

    if not fields:
        print(f"No fields found in class {class_name}")
        return None

    # Java 코드 생성
    java_code = f"package {package_name};\n\n"

    # import 문 추가
    imports = set()
    for field in fields:
        if field['java_type'].startswith('E'):
            imports.add(f"import com.bk.sbs.enums.{field['java_type']};")

    imports.add("import com.fasterxml.jackson.annotation.JsonAlias;")
    imports.add("import lombok.Data;")
    imports.add("import lombok.NoArgsConstructor;")
    imports.add("import lombok.Builder;")
    imports.add("import lombok.AllArgsConstructor;")

    for imp in sorted(imports):
        java_code += f"{imp}\n"

    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {class_name}\n"
    java_code += f" * Auto-generated from Unity C# {class_name} class\n"
    java_code += " */\n"
    java_code += "@Data\n"
    java_code += "@NoArgsConstructor\n"
    java_code += "@Builder\n"
    java_code += "@AllArgsConstructor\n"
    java_code += f"public class {class_name} {{\n"

    # 필드 정의
    for field in fields:
        java_code += f"    @JsonAlias(\"{field['csharp_name']}\")\n"
        java_code += f"    private {field['java_type']} {field['java_name']};\n\n"

    java_code = java_code.rstrip() + "\n}\n"

    # 출력 파일 경로
    output_file_path = os.path.join(output_dir, f"{class_name}.java")

    # 출력 디렉토리 생성
    os.makedirs(output_dir, exist_ok=True)

    # Java 파일 쓰기
    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(java_code)

    print(f"Java DTO file generated: {output_file_path}")
    print(f"  - Fields: {len(fields)}")
    for field in fields:
        print(f"    - {field['java_type']} {field['java_name']} (@JsonAlias(\"{field['csharp_name']}\"))")

    return output_file_path

if __name__ == "__main__":
    csharp_file_path = r"../../../thefirst_client_unity/Assets/Scripts/System/Data/DataTableModule.cs"
    output_dir = r"../../src/main/java/com/bk/sbs/dto"
    package_name = "com.bk.sbs.dto"
    class_name = "ModuleData"

    output_file = generate_java_dto(csharp_file_path, output_dir, package_name, class_name)

    if output_file:
        open_file_location(output_file)
